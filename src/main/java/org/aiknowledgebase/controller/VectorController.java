package org.aiknowledgebase.controller;

import org.aiknowledgebase.dto.ChunkSearchResult;
import org.aiknowledgebase.service.VectorStoreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vector")
public class VectorController {

    private final VectorStoreService vectorStoreService;

    public VectorController(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 为某个知识库下的所有文本切片构建向量。
     */
    @PostMapping("/build")
    public Map<String, Object> buildEmbeddings(@RequestParam Long knowledgeBaseId) {
        int count = vectorStoreService.buildEmbeddings(knowledgeBaseId);

        return Map.of(
                "knowledgeBaseId", knowledgeBaseId,
                "embeddedChunkCount", count,
                "message", "向量构建完成"
        );
    }

    /**
     * 根据用户问题检索相似文本切片。
     */
    @GetMapping("/search")
    public List<ChunkSearchResult> searchSimilarChunks(@RequestParam Long knowledgeBaseId,
                                                       @RequestParam String query,
                                                       @RequestParam(defaultValue = "3") int topK) {
        return vectorStoreService.searchSimilarChunks(knowledgeBaseId, query, topK);
    }
}