package com.example;

import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class StorageService {
    private final S3Client s3;
    private final Map<String, Double> pricePerGb;

    public record ClassifyStats(long tagged, long skipped) {}

    public StorageService(S3Client s3, Environment env) {
        this.s3 = s3;
        // Bind app.s3.pricePerGb -> Map<String, Double>
        this.pricePerGb = Binder.get(env)
                .bind("app.s3.price-per-gb", Bindable.mapOf(String.class, Double.class)) // try kebab-case first
                .orElseGet(() -> Binder.get(env)
                        .bind("app.s3.pricePerGb", Bindable.mapOf(String.class, Double.class)) // fall back to camelCase
                        .orElse(Map.of(
                                "STANDARD", 0.033,
                                "STANDARD_IA", 0.0198,
                                "ONEZONE_IA", 0.0152,
                                "GLACIER_IR", 0.0052,
                                "GLACIER", 0.0045,
                                "DEEP_ARCHIVE", 0.0012
                        )));
    }

    public ClassifyStats classifyObjects(ClassifyRequest req) {
        long tagged = 0, skipped = 0;
        var now = Instant.now();
        String contToken = null;

        do {
            var listReq = ListObjectsV2Request.builder()
                    .bucket(req.bucket()).prefix(req.prefix()).continuationToken(contToken).build();
            var page = s3.listObjectsV2(listReq);
            for (var o : page.contents()) {
                var ageDays = Duration.between(o.lastModified(), now).toDays();
                String tier = ageDays >= req.archiveAfterDays() ? "archive"
                        : ageDays >= req.warmAfterDays()    ? "warm"
                        : "hot";
                try {
                    s3.putObjectTagging(PutObjectTaggingRequest.builder()
                            .bucket(req.bucket()).key(o.key())
                            .tagging(Tagging.builder().tagSet(Tag.builder().key("tier").value(tier).build()).build())
                            .build());
                    tagged++;
                } catch (S3Exception e) { skipped++; }
            }
            contToken = page.nextContinuationToken();
        } while (contToken != null);

        return new ClassifyStats(tagged, skipped);
    }

    public SavingsReport estimateSavings(String bucket, String prefix) {
        long bytes = 0; long count = 0;
        double current = 0.0, projected = 0.0;
        String contToken = null;

        do {
            var listReq = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).continuationToken(contToken).build();
            var page = s3.listObjectsV2(listReq);

            for (var o : page.contents()) {
                count++; bytes += o.size();
                String currentClass = o.storageClassAsString() == null ? "STANDARD" : o.storageClassAsString();
                double gb = Math.max(0.000001, (o.size() / 1024.0 / 1024.0 / 1024.0));
                current += gb * priceOrZero(currentClass);

                String projectedClass = "STANDARD";
                try {
                    var tags = s3.getObjectTagging(GetObjectTaggingRequest.builder().bucket(bucket).key(o.key()).build()).tagSet();
                    var tier = tags.stream().filter(t -> t.key().equals("tier")).findFirst().map(Tag::value).orElse("hot");
                    projectedClass = switch (tier) {
                        case "warm" -> "STANDARD_IA";
                        case "archive" -> "GLACIER_IR";
                        default -> "STANDARD";
                    };
                } catch (S3Exception e) { /* ignore and assume STANDARD */ }
                projected += gb * priceOrZero(projectedClass);
            }
            contToken = page.nextContinuationToken();
        } while (contToken != null);

        return new SavingsReport(bucket, prefix, round2(current), round2(projected), count, bytes);
    }

    private double priceOrZero(String storageClass) {
        return pricePerGb.getOrDefault(storageClass, 0.0);
    }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
