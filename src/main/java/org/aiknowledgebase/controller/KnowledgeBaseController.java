package org.aiknowledgebase.controller;

import org.aiknowledgebase.dto.CreateKnowledgeBaseRequest;
import org.aiknowledgebase.entity.KnowledgeBase;
import org.aiknowledgebase.repository.KnowledgeBaseRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kbs")
public class KnowledgeBaseController {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseController(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * 创建知识库
     */
    @PostMapping
    public KnowledgeBase createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        return knowledgeBaseRepository.save(knowledgeBase);
    }

    /**
     * 查询所有知识库
     */
    @GetMapping
    public List<KnowledgeBase> listKnowledgeBases() {
        return knowledgeBaseRepository.findAll();
    }
}