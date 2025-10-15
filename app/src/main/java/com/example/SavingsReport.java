package com.example;

public record SavingsReport(
    String bucket, String prefix,
    double currentMonthlyAud, double projectedMonthlyAud,
    long objectCount, long bytesAnalyzed) {}
