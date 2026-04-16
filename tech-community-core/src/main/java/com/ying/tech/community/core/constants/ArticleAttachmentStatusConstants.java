package com.ying.tech.community.core.constants;

/**
 * 文章附件状态常量。
 * 用于描述附件从上传成功到绑定文章，再到逻辑删除的生命周期状态。
 */
public final class ArticleAttachmentStatusConstants {
    /**
     * 附件已经上传到对象存储，但尚未绑定到具体文章。
     */
    public static final int UPLOADED = 0;

    /**
     * 附件已经绑定到某篇文章，属于可展示状态。
     */
    public static final int BOUND = 1;

    /**
     * 附件已经被业务标记为删除，不再参与正常查询。
     */
    public static final int DELETED = 2;

    private ArticleAttachmentStatusConstants() {
    }
}
