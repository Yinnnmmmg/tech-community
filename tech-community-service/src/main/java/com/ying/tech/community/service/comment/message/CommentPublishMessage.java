package com.ying.tech.community.service.comment.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentPublishMessage implements Serializable {
    private Long commentId;
    private Long authorId;
    private Long publishTime;
    private String reason;
}
