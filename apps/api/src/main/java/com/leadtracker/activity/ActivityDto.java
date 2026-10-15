package com.leadtracker.activity;

import com.leadtracker.lead.LeadDto.UserRef;
import java.time.Instant;
import java.util.UUID;

public record ActivityDto(UUID id, UUID leadId, ActivityType type, String body, UserRef user, Instant createdAt) {}
