package org.aiknowledgebase.dto;

import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    private String name;

    private String description;
}