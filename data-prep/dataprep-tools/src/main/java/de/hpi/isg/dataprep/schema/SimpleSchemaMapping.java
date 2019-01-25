package de.hpi.isg.dataprep.schema;

import de.hpi.isg.dataprep.model.target.schema.Attribute;
import de.hpi.isg.dataprep.model.target.schema.Schema;
import de.hpi.isg.dataprep.model.target.schema.SchemaMapping;
import de.hpi.isg.dataprep.model.target.schema.Transform;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents the schema mapping from the current schema to the target schema. It is used by
 * the decision engine to calculate the applicability score
 * for preparator suggestion.
 *
 * @author lan.jiang
 * @since 12/18/18
 */
public class SimpleSchemaMapping implements SchemaMapping {

    private Schema sourceSchema;
    private Schema currentSchema;
    private Schema targetSchema;

    /**
     * Each key represents a particular attribute in the source schema, its value is a set of attributes
     * in the target schema that are derived from the attribute in the key.
     */
    private Map<Attribute, Set<Attribute>> mapping;

    private List<SchemaMappingNode> mappingNodes;

    public SimpleSchemaMapping(Schema sourceSchema, Schema targetSchema,
                               List<SchemaMappingNode> mappingNodes) {
        this(sourceSchema);
        this.targetSchema = targetSchema;
//        this.mapping = mapping;
        this.mappingNodes = mappingNodes;
    }

    public SimpleSchemaMapping(Schema sourceSchema) {
        this.sourceSchema = sourceSchema;
        this.currentSchema = sourceSchema;

        this.mappingNodes = new LinkedList<>();
        for (Attribute attribute : this.currentSchema.getAttributes()) {
            SchemaMappingNode node = new SchemaMappingNode(attribute);
            node.update();
            mappingNodes.add(node);
        }
    }

    @Override
    public Schema getSourceSchema() {
        return sourceSchema;
    }

    @Override
    public Schema getCurrentSchema() {
        return currentSchema;
    }

    @Override
    public Schema getTargetSchema() {
        return targetSchema;
    }

    @Override
    public boolean hasMapped() {
        return currentSchema.equals(targetSchema);
    }

    /**
     * Get the set of attributes in the target schema that are derived from the given {@link Attribute}.
     *
     * @param attribute in the source schema
     * @return the set of attributes in the target schema that are derived from the given attribute. If
     * the given attribute does not exist in the source schema, return null.
     */
    public Set<Attribute> getTargetBySourceAttribute(Attribute attribute) {
        return mapping.getOrDefault(attribute, null);
    }

    @Override
    public Set<Attribute> getTargetBySourceAttributeName(String attributeName) {
        Optional<Set<Attribute>> oTargetAttributes = mapping.entrySet().stream()
                .filter(attrMapping -> attrMapping.getKey().getName().equals(attributeName))
                .map(attrMapping -> attrMapping.getValue())
                .findFirst();
        return oTargetAttributes.orElse(null);
    }

    @Override
    public Set<Attribute> getSourceByTargetAttribute(Attribute attribute) {
        Set<Attribute> sourceAttributes = mapping.entrySet().stream()
                .filter(attrMapping -> attrMapping.getValue().contains(attribute))
                .map(attrMapping -> attrMapping.getKey())
                .collect(Collectors.toSet());
        return sourceAttributes.size()==0?null:sourceAttributes;
    }

    public Set<Attribute> getSourceByTargetAttributeName(String attributeName) {
        Set<Attribute> sourceAttributes = mapping.entrySet().stream()
                .filter(attrMapping -> {
                    long countAttr = attrMapping.getValue().stream()
                            .filter(attribute -> attribute.getName().equals(attributeName)).count();
                    return countAttr>0?true:false;
                })
                .map(attrMapping -> attrMapping.getKey())
                .collect(Collectors.toSet());
        return sourceAttributes.size()==0?null:sourceAttributes;
    }

//    @Override
//    public void constructSchemaMapping(List<Transform> transforms) {
//        for (Transform transform : transforms) {
//            transform.reformSchema(this);
//        }
//    }

    @Override
    public void updateSchemaMappingNodes() {
        mappingNodes.stream()
                .map(node -> findLastNodesOfChain(node))
                .flatMap(lastNodes -> lastNodes.stream())
                .forEach(node -> {
                    if (!node.updated) {
                        node.update();
                    }
                });
    }

    @Override
    public void updateMapping(Attribute sourceAttribute, Attribute targetAttribute) {
        if (sourceAttribute == null) {
            throw new RuntimeException("Source attribute can not be found in the current schema.");
        }
        if (targetAttribute != null) {

            List<SchemaMappingNode> tails = mappingNodes.stream()
                    .map(node -> findLastUpdatedNodesOfChain(node))
                    .flatMap(lastNodes -> lastNodes.stream())
                    .collect(Collectors.toList());
            SchemaMappingNode sourceNode = tails.stream()
                    .filter(node -> node.attribute.equals(sourceAttribute))
                    .findFirst()
                    .get();
            if (sourceNode.next == null) {
                sourceNode.next = new LinkedList<>();
            }
            sourceNode.next.add(new SchemaMappingNode(targetAttribute, sourceNode.getLayer()+1));
        } else {
            // target is null, saying that a delete transform was just executed.
            // pass
        }
    }

    @Override
    public void updateSchema(Schema latestSchema) {
        this.currentSchema = latestSchema;
    }

    @Override
    public void updateSchema() {
        List<SchemaMappingNode> tails = mappingNodes.stream()
                .map(node -> findLastUpdatedNodesOfChain(node))
                .flatMap(lastNodes -> lastNodes.stream())
                .distinct()
                .collect(Collectors.toList());
        int maxLayer = tails.stream().max(Comparator.comparingInt(node -> node.getLayer())).get().getLayer();
        tails = tails.stream().filter(node -> node.getLayer()==maxLayer).collect(Collectors.toList());

        List<Attribute> latestAttribute = new LinkedList<>();
        tails.stream().forEachOrdered(node -> {
            latestAttribute.add(node.getAttribute());
        });
        this.currentSchema = new Schema(latestAttribute);
    }

    @Override
    public SchemaMapping createSchemaMapping() {
        SchemaMapping newInstance = new SimpleSchemaMapping(this.sourceSchema, this.currentSchema, mappingNodes);
        return newInstance;
    }

    private static List<SchemaMappingNode> findLastUpdatedNodesOfChain(SchemaMappingNode node) {
        List<SchemaMappingNode> lastNodes = new LinkedList<>();
        Queue<SchemaMappingNode> iterator = new LinkedList<>();
        if (node.updated) {
            iterator.offer(node);
        }
        while (!iterator.isEmpty()) {
            SchemaMappingNode first = iterator.poll();
            if (first.next == null) {
                lastNodes.add(first);
            } else {
                if (first.next.stream().filter(oneOfNext -> oneOfNext.updated).count() == 0) {
                    lastNodes.add(first);
                    continue;
                }
                for (SchemaMappingNode oneOfNext : first.next) {
                    if (oneOfNext.updated) {
                        iterator.offer(oneOfNext);
                    }
                }
            }
        }
        return lastNodes;
    }

    private static List<SchemaMappingNode> findLastNodesOfChain(SchemaMappingNode node) {
        List<SchemaMappingNode> lastNodes = new LinkedList<>();
        Queue<SchemaMappingNode> iterator = new LinkedList<>();
        iterator.offer(node);
        while (!iterator.isEmpty()) {
            SchemaMappingNode first = iterator.poll();
            if (first.next == null) {
                lastNodes.add(first);
            } else {
                for (SchemaMappingNode oneOfNext : first.next) {
                    iterator.offer(oneOfNext);
                }
            }
        }
        return lastNodes;
    }

    public class SchemaMappingNode {

        private Attribute attribute;

        private List<SchemaMappingNode> next;

        private int layer = 0;

        private boolean updated = false;

        public SchemaMappingNode(Attribute attribute) {
            this.attribute = attribute;
        }

        public SchemaMappingNode(Attribute attribute, int layer) {
            this(attribute);
            this.layer = layer;
        }

        public Attribute getAttribute() {
            return attribute;
        }

        public int getLayer() {
            return layer;
        }

        public void update() {
            this.updated = true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SchemaMappingNode that = (SchemaMappingNode) o;
            return layer == that.layer &&
                    Objects.equals(attribute, that.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attribute, layer);
        }
    }
}
