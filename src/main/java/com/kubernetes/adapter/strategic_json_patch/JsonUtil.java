package com.kubernetes.adapter.strategic_json_patch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class JsonUtil {

    /**
     * just for part of strategic patch json
     * @return
     */
    public static JsonNode applyStrategicJsonPatchToJson(String strategicStr, JsonNode targetNode) {

        if (Objects.isNull(strategicStr) || strategicStr.trim().length() <= 0) {
            return null;
        } else {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonNode = objectMapper.readTree(strategicStr);
                String path = "";

                Map<String, Object> maps = new HashMap<>();
                findStrategicPartPatch(jsonNode, path, maps);

                if (Objects.nonNull(maps.get("strategicNode"))) {
                   JsonNode strategicNode = (JsonNode) maps.get("strategicNode");
                    Iterator<String> fields = strategicNode.fieldNames();
                    while (fields.hasNext()) {
                        String field = fields.next();
                        if (field.startsWith("$")) {
                            JsonNode order = strategicNode.get(field);
                            ArrayNode orderArray = (ArrayNode) order;
                            String descField =  orderArray.get(0).fieldNames().next();
                            //get des
                            String [] buffer = field.split("/");
                            JsonNode desNode = strategicNode.get(buffer[1]);
                            ArrayNode arrayNode = (ArrayNode) desNode;
                            JsonNode target = findValueByPath((String) maps.get("path"), targetNode);
                            for (JsonNode node : arrayNode) {

                                boolean exist = false;
                                //check exist from  strategic, check exist from target node
                                int index = 0;
                                for (JsonNode orderNode : orderArray) {

                                    if (Objects.nonNull(node.get("$patch"))) {
                                        //delete
                                        ArrayNode tArray = (ArrayNode) target.get(buffer[1]);

                                        int count = 0;
                                        for (JsonNode jsonObj : tArray) {
                                            if (jsonObj.get(descField).asText().equals(node.get(descField).asText())) {
                                                tArray.remove(count);
                                                break;
                                            }
                                            count++;
                                        }

                                    } else if (orderNode.get(descField).asText().equals(node.get(descField).asText())) {
                                        //add or replace
                                        ArrayNode tArray = (ArrayNode) target.get(buffer[1]);
                                        if (index >= tArray.size()) {
                                            break;
                                        }
                                        JsonNode tnode = tArray.get(index);
                                        if (tnode.get(descField).asText().equals(node.get(descField).asText())) {
                                            //replace
                                            copyFromTo(node, tnode);

                                        } else {
                                            //add
                                            tArray.add(node);
                                        }
                                        break;
                                    }
                                    index++;
                                }

                            }
                            break;
                        }
                    }

                }
                JsonMergePatch jsonMergePatch = JsonMergePatch.fromJson(jsonNode);
                JsonNode whole = jsonMergePatch.apply(targetNode);
                System.out.println(whole.toPrettyString());
                return whole;

            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            } catch (JsonPatchException e) {
                e.printStackTrace();
                return null;
            }
        }

    }

    public static void copyFromTo(JsonNode from, JsonNode to) {
        Iterator<String> iterator = from.fieldNames();
        ObjectNode objectNode = (ObjectNode) to;
        while (iterator.hasNext()) {
            String filed = iterator.next();
            objectNode.put(filed, from.get(filed));
        }
    }

    public static JsonNode findValueByPath(String path, JsonNode from) {
        String[] buffer = path.split("/");
        JsonNode bufferNode = from;
        for (String s : buffer) {
            if (null != s && !"".equals(s)) {
                bufferNode = bufferNode.get(s);
            }
        }
        return bufferNode;
    }

    private static void findStrategicPartPatch(JsonNode jsonNode, String pathBuffer, Map<String, Object> maps) {
        Iterator<String> fields = jsonNode.fieldNames();
        String path = "" + pathBuffer;
        while (fields.hasNext()) {
            String field = fields.next();
            JsonNode buffer = jsonNode.get(field);
            if (buffer.isMissingNode()) {
                break;
            }

            if (field.startsWith("$")) {
                maps.put("strategicNode", jsonNode.deepCopy());
                //delete strategic node
                ObjectNode objectNode = (ObjectNode) jsonNode;
                objectNode.remove(field);
                String[] strNodeName = field.split("/");
                objectNode.remove(strNodeName[1]);
                maps.put("path", path);
                return;
            }
            path = "/" + field;
            findStrategicPartPatch(buffer, path, maps);
        }
    }

    public static void main(String[] args) throws JsonProcessingException {
        String buffer = "{\"spec\":{\"$setElementOrder/ports\":[{\"port\":3306},{\"port\":81}],\"ports\":[{\"nodePort\":30402,\"port\":81}]}}";
        String targetJson = "{\"metadata\":{\"uid\":\"a9339e9c-1e2e-4ea2-a6ef-bd27bd36008f\",\"managedFields\":[{\"apiVersion\":\"v1\",\"manager\":\"kubectl-client-side-apply\",\"fieldsV1\":{\"f:metadata\":{\"f:annotations\":{\"f:ssc/external-net-id\":{},\"f:ssc/network-id\":{},\"f:kubectl.kubernetes.io/last-applied-configuration\":{},\".\":{},\"f:ssc/subnet-id\":{}},\"f:labels\":{\"f:app\":{},\".\":{}}},\"f:spec\":{\"f:type\":{},\"f:selector\":{\"f:app\":{},\".\":{}},\"f:sessionAffinity\":{},\"f:externalTrafficPolicy\":{},\"f:ports\":{\"k:{\\\"port\\\":80,\\\"protocol\\\":\\\"TCP\\\"}\":{\"f:port\":{},\"f:targetPort\":{},\"f:protocol\":{},\"f:name\":{},\".\":{}},\".\":{}}}},\"time\":\"2022-01-04T06:58:01Z\",\"operation\":\"Update\",\"fieldsType\":\"FieldsV1\"}],\"resourceVersion\":\"7611902\",\"name\":\"svc-test3\",\"namespace\":\"wdh\",\"creationTimestamp\":\"2022-01-04T06:58:01Z\",\"annotations\":{\"ssc/subnet-id\":\"c07fda56-a0dd-4717-9697-25294d558f91\",\"ssc/external-net-id\":\"cfed230f-eb66-4f47-93ac-d3550a4774fa\",\"kubectl.kubernetes.io/last-applied-configuration\":\"{\\\"apiVersion\\\":\\\"v1\\\",\\\"kind\\\":\\\"Service\\\",\\\"metadata\\\":{\\\"annotations\\\":{\\\"ssc/external-net-id\\\":\\\"cfed230f-eb66-4f47-93ac-d3550a4774fa\\\",\\\"ssc/network-id\\\":\\\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\\\",\\\"ssc/subnet-id\\\":\\\"c07fda56-a0dd-4717-9697-25294d558f91\\\"},\\\"labels\\\":{\\\"app\\\":\\\"svc-test3\\\"},\\\"name\\\":\\\"svc-test3\\\",\\\"namespace\\\":\\\"wdh\\\"},\\\"spec\\\":{\\\"ports\\\":[{\\\"name\\\":\\\"http2\\\",\\\"nodePort\\\":30525,\\\"port\\\":80,\\\"targetPort\\\":81}],\\\"selector\\\":{\\\"app\\\":\\\"svc-test3\\\"},\\\"type\\\":\\\"NodePort\\\"}}\\n\",\"ssc/network-id\":\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\"},\"selfLink\":\"/api/v1/namespaces/wdh/services/svc-test3\",\"labels\":{\"app\":\"svc-test3\"}},\"apiVersion\":\"v1\",\"kind\":\"Service\",\"spec\":{\"clusterIPs\":[\"172.22.7.95\"],\"sessionAffinity\":\"None\",\"selector\":{\"app\":\"svc-test3\"},\"externalTrafficPolicy\":\"Cluster\",\"ports\":[{\"protocol\":\"TCP\",\"port\":80,\"name\":\"http2\",\"targetPort\":81,\"nodePort\":30525}],\"type\":\"NodePort\",\"clusterIP\":\"172.22.7.95\"},\"status\":{\"loadBalancer\":{}}}";
        applyStrategicJsonPatchToJson(buffer, new ObjectMapper().readTree(targetJson));
    }
}
