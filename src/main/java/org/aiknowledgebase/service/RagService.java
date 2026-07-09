package org.aiknowledgebase.service;

import org.aiknowledgebase.dto.ChunkSearchResult;
import org.aiknowledgebase.dto.RagAnswerResponse;
import org.aiknowledgebase.dto.RagSource;
import org.aiknowledgebase.dto.WebSearchResult;
import org.aiknowledgebase.entity.ChatMessage;
import org.aiknowledgebase.entity.ChatSession;
import org.aiknowledgebase.repository.ChatMessageRepository;
import org.aiknowledgebase.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@Service
public class RagService {

    private final VectorStoreService vectorStoreService;
    private final LlmService llmService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSearchService webSearchService;

    public RagService(VectorStoreService vectorStoreService,
                      LlmService llmService,
                      ChatSessionRepository chatSessionRepository,
                      ChatMessageRepository chatMessageRepository,
                      WebSearchService webSearchService) {
        this.vectorStoreService = vectorStoreService;
        this.llmService = llmService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.webSearchService = webSearchService;
    }

    /**
     * 普通非流式问答。
     */
    @Transactional
    public RagAnswerResponse ask(Long userId,
                                 Long knowledgeBaseId,
                                 String question,
                                 int topK,
                                 String sessionId,
                                 Long uploadedFileId,
                                 boolean webSearch) {

        String normalizedQuestion = question == null ? "" : question.trim();

        if (normalizedQuestion.isBlank()) {
            String newSessionId = getOrCreateSessionId(userId, sessionId, "空问题");
            return new RagAnswerResponse(
                    newSessionId,
                    question,
                    "请输入有效问题。",
                    List.of()
            );
        }

        String currentSessionId = getOrCreateSessionId(userId, sessionId, normalizedQuestion);

        // 注意：这里先取历史消息，再保存当前问题。
        // 这样 history 里不包含当前问题，方便做“上下文问题改写”。
        List<ChatMessage> historyMessages = getRecentHistoryMessages(currentSessionId);
        saveMessage(currentSessionId, "USER", normalizedQuestion);

        String history = buildHistory(historyMessages);

        /*
         * 新增：根据历史对话，把当前问题改写成适合检索/搜索的完整问题。
         *
         * 例如：
         * 上文：NBA历史得分最高的一场比赛是谁比谁，比分是多少？
         * 当前：那么世界杯呢？
         * 改写：世界杯历史上单场进球最多的一场比赛是谁对谁，比分是多少？
         */
        String retrievalQuestion = rewriteQuestionForRetrieval(normalizedQuestion, history);

        System.out.println("用户原问题 = " + normalizedQuestion);
        System.out.println("改写后的检索问题 = " + retrievalQuestion);

        /*
         * 优先级 1：
         * 如果前端传了 uploadedFileId，说明用户正在问当前上传文件。
         * 这种情况下必须只检索当前上传文件，不走联网搜索。
         */
        if (uploadedFileId != null) {
            System.out.println("RagService：只检索当前上传文件 uploadedFileId = " + uploadedFileId);

            List<ChunkSearchResult> uploadedFileChunks =
                    vectorStoreService.searchUploadedFileChunksWithOverview(
                            uploadedFileId,
                            retrievalQuestion,
                            topK
                    );

            if (uploadedFileChunks == null || uploadedFileChunks.isEmpty()) {
                String answer = "当前文件还没有可检索的文本内容，可能是文件尚未完成向量化，或者文件解析失败。";

                saveMessage(currentSessionId, "ASSISTANT", answer);

                return new RagAnswerResponse(
                        currentSessionId,
                        question,
                        answer,
                        List.of()
                );
            }

            String context = buildContext(uploadedFileChunks);
            String prompt = buildUploadedFilePrompt(normalizedQuestion, context, history);

            List<RagSource> sources = buildRagSources(uploadedFileChunks);

            String answer = llmService.chat(prompt);

            saveMessage(currentSessionId, "ASSISTANT", answer);

            return new RagAnswerResponse(
                    currentSessionId,
                    question,
                    answer,
                    sources
            );
        }

        /*
         * 优先级 2：
         * 如果没有指定上传文件，并且前端开启了联网搜索，则调用 Tavily 搜索。
         */
        if (webSearch) {
            System.out.println("RagService：执行联网搜索 query = " + retrievalQuestion);

            List<WebSearchResult> webResults = webSearchService.search(
                    retrievalQuestion,
                    Math.max(topK, 5)
            );

            if (webResults == null || webResults.isEmpty()) {
                String answer = "联网搜索没有找到可用结果，请换个问题再试。";

                saveMessage(currentSessionId, "ASSISTANT", answer);

                return new RagAnswerResponse(
                        currentSessionId,
                        question,
                        answer,
                        List.of()
                );
            }

            String webContext = buildWebSearchContext(webResults);
            String prompt = buildWebSearchPrompt(normalizedQuestion, webContext, history);

            String answer = llmService.chat(prompt);

            saveMessage(currentSessionId, "ASSISTANT", answer);

            /*
             * RagAnswerResponse 目前的 sources 类型是 RagSource，
             * 无法直接承载网页 title/url，所以非流式接口这里不返回网页来源。
             * 前端主流程使用 streamAsk，可以正常展示网页来源。
             */
            return new RagAnswerResponse(
                    currentSessionId,
                    question,
                    answer,
                    List.of()
            );
        }

        /*
         * 优先级 3：
         * 默认查本地知识库。
         */
        System.out.println("RagService：检索全部知识库 knowledgeBaseId = " + knowledgeBaseId);

        List<ChunkSearchResult> chunks = vectorStoreService.searchSimilarChunks(
                knowledgeBaseId,
                retrievalQuestion,
                topK
        );

        if (chunks == null || chunks.isEmpty()) {
            String prompt = buildGeneralChatPrompt(normalizedQuestion, history);
            String answer = llmService.chat(prompt);

            saveMessage(currentSessionId, "ASSISTANT", answer);

            return new RagAnswerResponse(
                    currentSessionId,
                    question,
                    answer,
                    List.of()
            );
        }

        String context = buildContext(chunks);
        boolean relevant = judgeKnowledgeRelevant(retrievalQuestion, context, history);

        if (!relevant) {
            String prompt = buildGeneralChatPrompt(normalizedQuestion, history);
            String answer = llmService.chat(prompt);

            saveMessage(currentSessionId, "ASSISTANT", answer);

            return new RagAnswerResponse(
                    currentSessionId,
                    question,
                    answer,
                    List.of()
            );
        }

        String ragPrompt = buildRagPrompt(normalizedQuestion, context, history);
        List<RagSource> sources = buildRagSources(chunks);

        String answer = llmService.chat(ragPrompt);

        saveMessage(currentSessionId, "ASSISTANT", answer);

        return new RagAnswerResponse(
                currentSessionId,
                question,
                answer,
                sources
        );
    }

    /**
     * 流式问答。
     * sendEvent 的 type 包括：
     * sessionId / delta / sources / error / done
     */
    @Transactional
    public void streamAsk(Long userId,
                          Long knowledgeBaseId,
                          String question,
                          int topK,
                          String sessionId,
                          Long uploadedFileId,
                          boolean webSearch,
                          BiConsumer<String, Object> sendEvent) {

        try {
            String normalizedQuestion = question == null ? "" : question.trim();

            if (normalizedQuestion.isBlank()) {
                String newSessionId = getOrCreateSessionId(userId, sessionId, "空问题");

                sendEvent.accept("sessionId", newSessionId);
                sendEvent.accept("delta", "请输入有效问题。");
                sendEvent.accept("sources", List.of());
                sendEvent.accept("done", "done");
                return;
            }

            String currentSessionId = getOrCreateSessionId(userId, sessionId, normalizedQuestion);
            sendEvent.accept("sessionId", currentSessionId);

            // 注意：先取历史消息，再保存当前问题。
            List<ChatMessage> historyMessages = getRecentHistoryMessages(currentSessionId);
            saveMessage(currentSessionId, "USER", normalizedQuestion);

            String history = buildHistory(historyMessages);

            /*
             * 新增：根据历史对话改写检索问题。
             * 这个 retrievalQuestion 只用于检索/搜索，不直接替代用户原问题。
             */
            String retrievalQuestion = rewriteQuestionForRetrieval(normalizedQuestion, history);

            System.out.println("用户原问题 = " + normalizedQuestion);
            System.out.println("改写后的检索问题 = " + retrievalQuestion);

            String prompt;
            List<?> sources;

            /*
             * 优先级 1：当前上传文件。
             * 如果有 uploadedFileId，就强制只查该文件，不联网。
             */
            if (uploadedFileId != null) {
                System.out.println("RagService Stream：只检索当前上传文件 uploadedFileId = " + uploadedFileId);

                List<ChunkSearchResult> uploadedFileChunks =
                        vectorStoreService.searchUploadedFileChunksWithOverview(
                                uploadedFileId,
                                retrievalQuestion,
                                topK
                        );

                if (uploadedFileChunks == null || uploadedFileChunks.isEmpty()) {
                    String answer = "当前文件还没有可检索的文本内容，可能是文件尚未完成向量化，或者文件解析失败。";

                    sendEvent.accept("delta", answer);
                    sendEvent.accept("sources", List.of());
                    sendEvent.accept("done", "done");

                    saveMessage(currentSessionId, "ASSISTANT", answer);
                    return;
                }

                String context = buildContext(uploadedFileChunks);
                prompt = buildUploadedFilePrompt(normalizedQuestion, context, history);
                sources = buildRagSources(uploadedFileChunks);

                /*
                 * 优先级 2：联网搜索。
                 */
            } else if (webSearch) {
                System.out.println("RagService Stream：执行联网搜索 query = " + retrievalQuestion);

                List<WebSearchResult> webResults = webSearchService.search(
                        retrievalQuestion,
                        Math.max(topK, 5)
                );

                if (webResults == null || webResults.isEmpty()) {
                    String answer = "联网搜索没有找到可用结果，请换个问题再试。";

                    sendEvent.accept("delta", answer);
                    sendEvent.accept("sources", List.of());
                    sendEvent.accept("done", "done");

                    saveMessage(currentSessionId, "ASSISTANT", answer);
                    return;
                }

                String webContext = buildWebSearchContext(webResults);
                prompt = buildWebSearchPrompt(normalizedQuestion, webContext, history);
                sources = webResults;

                /*
                 * 优先级 3：本地知识库。
                 */
            } else {
                System.out.println("RagService Stream：检索全部知识库 knowledgeBaseId = " + knowledgeBaseId);

                List<ChunkSearchResult> chunks = vectorStoreService.searchSimilarChunks(
                        knowledgeBaseId,
                        retrievalQuestion,
                        topK
                );

                if (chunks == null || chunks.isEmpty()) {
                    prompt = buildGeneralChatPrompt(normalizedQuestion, history);
                    sources = List.of();

                } else {
                    String context = buildContext(chunks);
                    boolean relevant = judgeKnowledgeRelevant(retrievalQuestion, context, history);

                    if (!relevant) {
                        prompt = buildGeneralChatPrompt(normalizedQuestion, history);
                        sources = List.of();

                    } else {
                        prompt = buildRagPrompt(normalizedQuestion, context, history);
                        sources = buildRagSources(chunks);
                    }
                }
            }

            String answer = llmService.streamChat(
                    prompt,
                    delta -> sendEvent.accept("delta", delta)
            );

            saveMessage(currentSessionId, "ASSISTANT", answer);

            sendEvent.accept("sources", sources);
            sendEvent.accept("done", "done");

        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            sendEvent.accept("error", "流式问答失败：" + message);
            sendEvent.accept("done", "done");
        }
    }

    private List<RagSource> buildRagSources(List<ChunkSearchResult> chunks) {
        return chunks.stream()
                .map(chunk -> new RagSource(
                        chunk.getChunkId(),
                        chunk.getDocumentId(),
                        chunk.getChunkIndex(),
                        chunk.getDistance(),
                        chunk.getContent()
                ))
                .toList();
    }

    /**
     * 根据历史对话，把当前问题改写成独立完整问题。
     *
     * 只用于检索/联网搜索，不直接替代用户原始问题。
     */
    private String rewriteQuestionForRetrieval(String question, String history) {
        if (question == null || question.isBlank()) {
            return question;
        }

        if (history == null || history.isBlank() || history.contains("暂无历史对话")) {
            return question;
        }

        String prompt = """
                你是一个搜索查询改写助手。
                你的任务是根据“对话历史”，把“用户当前问题”改写成一个完整、独立、适合搜索或知识库检索的问题。
                
                【对话历史】
                %s
                
                【用户当前问题】
                %s
                
                【改写要求】
                1. 如果当前问题已经完整，直接返回原问题。
                2. 如果当前问题依赖上文，例如“那世界杯呢”“这个呢”“继续说”“那它呢”“第二个呢”“和上面相比呢”，请结合历史补全含义。
                3. 不要回答问题，只改写问题。
                4. 不要输出解释。
                5. 只输出一行改写后的问题。
                6. 不要输出“输出：”“改写后：”等前缀。
                
                【示例】
                对话历史：用户：NBA历史得分最高的一场比赛是谁比谁，比分是多少？
                当前问题：那么世界杯呢？
                输出：世界杯历史上单场进球最多的一场比赛是谁对谁，比分是多少？
                
                【输出】
                """.formatted(history, question);

        String rewritten = llmService.chat(prompt);

        if (rewritten == null) {
            return question;
        }

        rewritten = cleanupRewrittenQuestion(rewritten);

        if (rewritten.isBlank()) {
            return question;
        }

        if (rewritten.length() > 200) {
            return question;
        }

        if (isBadRewriteResult(rewritten)) {
            return question;
        }

        return rewritten;
    }

    private String cleanupRewrittenQuestion(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text.trim();

        cleaned = cleaned
                .replace("【输出】", "")
                .replace("输出：", "")
                .replace("输出:", "")
                .replace("改写后：", "")
                .replace("改写后:", "")
                .replace("改写问题：", "")
                .replace("改写问题:", "")
                .replace("问题：", "")
                .replace("问题:", "")
                .replace("```", "")
                .replace("\"", "")
                .replace("“", "")
                .replace("”", "")
                .trim();

        if (cleaned.contains("\n")) {
            cleaned = cleaned.split("\\R")[0].trim();
        }

        return cleaned;
    }

    private boolean isBadRewriteResult(String text) {
        if (text == null) {
            return true;
        }

        String lower = text.toLowerCase();

        return lower.contains("调用大模型失败")
                || lower.contains("模型未返回")
                || lower.contains("解析大模型响应失败")
                || lower.contains("未配置")
                || lower.contains("too many requests")
                || lower.contains("error")
                || lower.contains("exception");
    }

    private String buildUploadedFilePrompt(String question, String context, String history) {
        return """
                你是一个严谨的文档问答助手。
                用户当前正在询问刚上传的文件。
                你必须只基于【当前文件内容】回答，不要使用无关历史知识，不要编造。
                
                【对话历史】
                %s
                
                【当前文件内容】
                %s
                
                【用户问题】
                %s
                
                【回答要求】
                1. 如果用户让你总结文件，请概括文件主题、核心方法、主要贡献和应用场景。
                2. 如果文件内容是英文，请用中文回答。
                3. 不要回答与当前文件无关的内容。
                4. 如果当前文件内容不足以回答，请明确说明“当前文件片段中没有足够信息”。
                5. 回答要自然、准确、简洁。
                6. 最后写一句：依据：当前上传文件片段。
                
                【输出】
                直接输出最终回答，不要输出思考过程。
                """.formatted(history, context, question);
    }

    private String buildWebSearchPrompt(String question, String webContext, String history) {
        return """
                你是一个严谨的联网搜索问答助手。
                当前日期：%s。
                你需要根据【联网搜索结果】和【对话历史】回答用户问题。
                
                【对话历史】
                %s
                
                【联网搜索结果】
                %s
                
                【用户问题】
                %s
                
                【回答要求】
                1. 优先依据联网搜索结果回答。
                2. 如果用户当前问题依赖上文，例如“那么世界杯呢”“这个呢”，请结合对话历史理解真实问题。
                3. 如果搜索结果之间存在冲突，请说明不同来源存在差异。
                4. 不要编造搜索结果中没有的具体事实、数字、日期、机构或结论。
                5. 如果搜索结果不足以回答，请明确说明信息不足。
                6. 回答要使用中文，简洁准确。
                7. 如果涉及最新信息，请提醒用户以来源网页为准。
                8. 最后写一句：依据：联网搜索结果。
                
                【输出】
                直接输出最终回答，不要输出思考过程。
                """.formatted(LocalDate.now(), history, webContext, question);
    }

    private boolean judgeKnowledgeRelevant(String question, String context, String history) {
        String prompt = """
                你是一个知识库问答系统的相关性判断器。
                你的任务是判断“知识库内容”是否能够直接回答“用户当前问题”。
                
                【对话历史】
                %s
                
                【知识库内容】
                %s
                
                【用户当前问题】
                %s
                
                【判断标准】
                1. 如果知识库内容能够直接回答用户问题，输出 YES。
                2. 如果知识库内容只是泛泛相关，但不能真正回答用户问题，输出 NO。
                3. 如果用户是在闲聊、吐槽、问你是谁、问你能做什么、问上文内容，输出 NO。
                4. 如果问题需要结合对话历史而不是知识库内容，输出 NO。
                
                【输出要求】
                只能输出 YES 或 NO，不要输出其他内容。
                """.formatted(history, context, question);

        String result = llmService.chat(prompt);

        if (result == null) {
            return false;
        }

        String normalized = result.trim().toUpperCase();

        return normalized.startsWith("YES")
                || normalized.startsWith("是")
                || normalized.contains("YES");
    }

    private String getOrCreateSessionId(Long userId, String sessionId, String firstQuestion) {
        if (sessionId != null && !sessionId.isBlank()) {
            return chatSessionRepository.findBySessionIdAndUserId(sessionId, userId)
                    .map(ChatSession::getSessionId)
                    .orElseGet(() -> createSession(userId, sessionId, firstQuestion).getSessionId());
        }

        String newSessionId = UUID.randomUUID().toString();
        return createSession(userId, newSessionId, firstQuestion).getSessionId();
    }

    private ChatSession createSession(Long userId, String sessionId, String firstQuestion) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setTitle(buildSessionTitle(firstQuestion));
        return chatSessionRepository.save(session);
    }

    private String buildSessionTitle(String firstQuestion) {
        if (firstQuestion == null || firstQuestion.isBlank()) {
            return "新会话";
        }

        String title = firstQuestion.trim();

        if (title.length() > 30) {
            return title.substring(0, 30);
        }

        return title;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        chatMessageRepository.save(message);

        chatSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setUpdateTime(java.time.LocalDateTime.now());
            chatSessionRepository.save(session);
        });
    }

    private List<ChatMessage> getRecentHistoryMessages(String sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findTop10BySessionIdOrderByCreateTimeDesc(sessionId);
        Collections.reverse(messages);
        return messages;
    }

    private String buildHistory(List<ChatMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "暂无历史对话。";
        }

        StringBuilder builder = new StringBuilder();

        for (ChatMessage message : historyMessages) {
            builder.append("USER".equals(message.getRole()) ? "用户：" : "助手：")
                    .append(message.getContent())
                    .append("\n");
        }

        return builder.toString();
    }

    private String buildContext(List<ChunkSearchResult> chunks) {
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            ChunkSearchResult chunk = chunks.get(i);

            contextBuilder
                    .append("【知识片段 ")
                    .append(i + 1)
                    .append("】\n")
                    .append("文档 ID：")
                    .append(chunk.getDocumentId())
                    .append("，切片序号：")
                    .append(chunk.getChunkIndex())
                    .append("，相似度距离：")
                    .append(chunk.getDistance())
                    .append("\n")
                    .append(chunk.getContent())
                    .append("\n\n");
        }

        return contextBuilder.toString();
    }

    private String buildWebSearchContext(List<WebSearchResult> results) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            WebSearchResult result = results.get(i);

            builder.append("【网页来源 ")
                    .append(i + 1)
                    .append("】\n")
                    .append("标题：")
                    .append(nullToEmpty(result.getTitle()))
                    .append("\n")
                    .append("链接：")
                    .append(nullToEmpty(result.getUrl()))
                    .append("\n")
                    .append("相关性分数：")
                    .append(result.getScore())
                    .append("\n");

            if (result.getPublishedDate() != null && !result.getPublishedDate().isBlank()) {
                builder.append("发布时间：")
                        .append(result.getPublishedDate())
                        .append("\n");
            }

            builder.append("内容摘要：")
                    .append(trimToMax(nullToEmpty(result.getContent()), 1200))
                    .append("\n\n");
        }

        return builder.toString();
    }

    private String buildGeneralChatPrompt(String question, String history) {
        return """
                你是一个友好的中文 AI 助手，运行在一个企业知识库智能问答系统中。
                
                【对话历史】
                %s
                
                【用户当前问题】
                %s
                
                【回答要求】
                1. 请自然回答用户问题。
                2. 如果用户在吐槽或表达不满，请先简短回应，再说明可以继续帮助排查。
                3. 如果用户询问你是谁或你能做什么，可以说明你是一个企业知识库智能问答助手。
                4. 如果用户询问上一个问题或上文内容，请结合对话历史回答。
                5. 不要强行引用知识库内容。
                6. 不要说“知识库中未找到相关信息”。
                7. 回答要简洁、自然。
                
                【输出要求】
                直接输出最终回答，不要输出思考过程。
                """.formatted(history, question);
    }

    private String buildRagPrompt(String question, String context, String history) {
        return """
                你是一个严谨的企业知识库智能问答助手，需要结合对话历史和知识库内容回答用户问题。
                
                【对话历史】
                %s
                
                【知识库内容】
                %s
                
                【用户当前问题】
                %s
                
                【回答要求】
                1. 请优先依据“知识库内容”回答用户问题。
                2. 如果当前问题涉及上文，可以结合“对话历史”理解问题。
                3. 不要编造知识库中没有的具体事实、数字、名称或结论。
                4. 回答要简洁、准确。
                5. 如果使用了知识库内容，请在末尾标明“依据：知识片段 x”。
                6. 直接输出最终回答，不要复述 Prompt，不要输出思考过程。
                """.formatted(history, context, question);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String trimToMax(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}