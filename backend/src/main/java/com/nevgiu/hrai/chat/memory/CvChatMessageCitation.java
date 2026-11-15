package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.chat.CvChatResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Minimized navigation metadata for one authorized citation; CV excerpts are deliberately not duplicated here. */
@Entity
@Table(name = "cv_chat_message_citations")
class CvChatMessageCitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 80)
    private String reference;

    @Column(nullable = false)
    private Long candidateId;

    private Long documentId;
    private Integer pageNumber;

    protected CvChatMessageCitation() {
    }

    CvChatMessageCitation(Long messageId, int displayOrder, CvChatResponse.Citation citation) {
        this.messageId = messageId;
        this.displayOrder = displayOrder;
        this.reference = citation.reference();
        this.candidateId = citation.candidateId();
        this.documentId = citation.documentId();
        this.pageNumber = citation.pageNumber();
    }

    CvChatResponse.Citation response() {
        return new CvChatResponse.Citation(reference, candidateId, documentId, pageNumber, "");
    }
}
