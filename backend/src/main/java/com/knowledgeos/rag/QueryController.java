package com.knowledgeos.rag;

import com.knowledgeos.rag.dto.QueryRequest;
import com.knowledgeos.rag.dto.QueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 04_API_SPECIFICATION.md §4.1 — endpoint centrale del prodotto.
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryController {

    private final AnswerGenerationService answerGenerationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('VIEWER','DOCUMENT_MANAGER','KNOWLEDGE_EDITOR','TENANT_ADMIN')")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return answerGenerationService.answer(request);
    }
}
