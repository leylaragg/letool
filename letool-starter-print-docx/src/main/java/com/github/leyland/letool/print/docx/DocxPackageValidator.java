package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.exception.PrintValidationException;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.Base;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;

import java.util.List;
import java.util.Locale;

/**
 * 在保存前检查 DOCX 关系和部件没有越过框架安全边界。
 *
 * @author leyland
 */
final class DocxPackageValidator {

    /** 当前纵向链路允许生成的关系类型后缀。 */
    private static final List<String> ALLOWED_RELATIONSHIP_SUFFIXES = List.of(
            "/officeDocument",
            "/metadata/core-properties",
            "/extended-properties",
            "/styles",
            "/settings",
            "/webSettings",
            "/fontTable",
            "/theme",
            "/numbering",
            "/endnotes");

    /**
     * 校验整个 OOXML 包。
     *
     * @param wordPackage 待保存的 DOCX 包
     * @throws PrintValidationException 出现外部关系或危险部件时抛出
     */
    void validate(WordprocessingMLPackage wordPackage) {
        validateRelationships(wordPackage);
        for (Part part : wordPackage.getParts().getParts().values()) {
            validatePart(part);
            validateRelationships(part);
        }
        String mainXml = XmlUtils.marshaltoString(
                wordPackage.getMainDocumentPart().getJaxbElement(), false, false);
        if (mainXml.contains("<w:altChunk") || mainXml.contains(":altChunk")) {
            throw invalidPackage();
        }
    }

    /** 检查一组关系只指向包内允许的标准部件。 */
    private void validateRelationships(Base source) {
        RelationshipsPart relationshipsPart = source.getRelationshipsPart();
        if (relationshipsPart == null || relationshipsPart.getRelationships() == null) {
            return;
        }
        for (Relationship relationship
                : relationshipsPart.getRelationships().getRelationship()) {
            if ("External".equalsIgnoreCase(relationship.getTargetMode())
                    || !isAllowedRelationship(relationship.getType())) {
                throw invalidPackage();
            }
        }
    }

    /** 判断关系类型是否属于当前渲染器的闭合能力集合。 */
    private boolean isAllowedRelationship(String relationshipType) {
        if (relationshipType == null) {
            return false;
        }
        return ALLOWED_RELATIONSHIP_SUFFIXES.stream().anyMatch(relationshipType::endsWith);
    }

    /** 拒绝宏、ActiveX、OLE、嵌入对象和附件常用部件路径。 */
    private void validatePart(Part part) {
        String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
        String contentType = part.getContentType() == null
                ? "" : part.getContentType().toString().toLowerCase(Locale.ROOT);
        if (containsForbiddenMarker(partName) || containsForbiddenMarker(contentType)) {
            throw invalidPackage();
        }
    }

    /** 判断部件描述中是否含有明确禁止的能力标记。 */
    private boolean containsForbiddenMarker(String value) {
        return value.contains("vbaproject")
                || value.contains("macroenabled")
                || value.contains("activex")
                || value.contains("embeddings")
                || value.contains("oleobject")
                || value.contains("altchunk");
    }

    /** 返回不携带关系目标和部件名称的稳定校验异常。 */
    private PrintValidationException invalidPackage() {
        return PrintValidationException.invalidDocument("DOCX 包含不允许的关系或部件");
    }
}
