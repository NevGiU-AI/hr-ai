package com.nevgiu.hrai.security.audit;

import java.util.List;

public record SecurityAuditPageResponse(List<SecurityAuditEventResponse> content, int page, int size,
                                        long totalElements, int totalPages) {}
