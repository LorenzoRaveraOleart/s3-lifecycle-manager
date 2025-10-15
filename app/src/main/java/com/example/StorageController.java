package com.example;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/storage")
public class StorageController {
  private final StorageService svc;
  public StorageController(StorageService svc) { this.svc = svc; }

  @PostMapping("/classify")
  public Map<String,Object> classify(@RequestBody ClassifyRequest req) {
    var stats = svc.classifyObjects(req);
    return Map.of("status","ok", "tagged", stats.tagged(), "skipped", stats.skipped());
  }

  @GetMapping("/reports/s3-savings")
  public SavingsReport report(@RequestParam String bucket,
                              @RequestParam(defaultValue = "") String prefix) {
    return svc.estimateSavings(bucket, prefix);
  }
}
