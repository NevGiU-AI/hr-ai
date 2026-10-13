package com.nevgiu.hrai.candidate;

public record CvOriginalDownload(String filename, String contentType, long contentLength, byte[] content) {
}
