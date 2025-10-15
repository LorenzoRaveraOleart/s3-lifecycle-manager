package com.example;

public record ClassifyRequest(String bucket, String prefix, int warmAfterDays, int archiveAfterDays) {}
