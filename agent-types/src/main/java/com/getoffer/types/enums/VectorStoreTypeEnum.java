package com.getoffer.types.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 向量存储类型枚举
 *
 * @author getoffer
 * @since 2025-01-29
 */
public enum VectorStoreTypeEnum {

    /**
     * PGVector - PostgreSQL 向量扩展
     */
    PGVECTOR("pgvector"),

    /**
     * Chroma - 开源向量数据库
     */
    CHROMA("chroma"),

    /**
     * Faiss - Facebook AI Similarity Search
     */
    FAISS("faiss"),

    /**
     * Milvus - 开源向量数据库
     */
    MILVUS("milvus");

    private final String code;

    VectorStoreTypeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static VectorStoreTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (VectorStoreTypeEnum type : VectorStoreTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown vector store type code: " + code);
    }
}
