package com.kubernetes.adapter.strategic_json_patch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
                            //get des filed, like $setElementOrder/ports
                            String [] buffer = field.split("/");
                            JsonNode desNode = strategicNode.get(buffer[1]);
                            if (Objects.isNull(desNode)) {
                                return null;
                            }
                            ArrayNode arrayNode = (ArrayNode) desNode;
                            JsonNode target = findValueByPath((String) maps.get("path"), targetNode);
                            ArrayNode orderedArray = JsonNodeFactory.instance.arrayNode();// to order the new arr
                            for (JsonNode orderNode : orderArray) {

                                //check exist from  strategic, check exist from target node
                                int index = 0;
                                for (JsonNode node :  arrayNode) {

                                    if (Objects.nonNull(node.get("$patch"))) {
                                        //delete do nothing
                                        break;

                                    } else if (orderNode.get(descField).asText().equals(node.get(descField).asText())) {
                                        //add or replace
                                        ArrayNode tArray = (ArrayNode) target.get(buffer[1]);
                                        if (index >= tArray.size()) {
                                            break;
                                        }
                                        JsonNode tnode = tArray.get(index);
                                        if (tnode.get(descField).asText().equals(node.get(descField).asText())) {
                                            //replace
                                            JsonNode replaced = JsonNodeFactory.instance.objectNode();
                                            copyFromTo(node, tnode);
                                            copyFromTo(tnode, replaced);
                                            orderedArray.add(replaced);

                                        } else {
                                            //add
                                            tArray.add(node);
                                            orderedArray.add(node);
                                        }
                                        break;
                                    } else {
                                        //the node exist in target node
                                        ArrayNode fromTarget = (ArrayNode) target.get(buffer[1]);
                                        for (JsonNode childNode : fromTarget) {
                                            if (childNode.get(descField).asText().equals(orderNode.get(descField).asText())) {
                                                orderedArray.add(childNode.deepCopy());
                                                break;
                                            }
                                        }
                                    }
                                    index++;
                                }

                            }

                            ObjectNode targetObj = (ObjectNode) target;
                            targetObj.remove(buffer[1]);
                            targetObj.put(buffer[1], orderedArray);
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
        String buffer = "{\"metadata\":{\"annotations\":{\"kubectl.kubernetes.io/last-applied-configuration\":\"{\\\"apiVersion\\\":\\\"v1\\\",\\\"kind\\\":\\\"Service\\\",\\\"metadata\\\":{\\\"annotations\\\":{\\\"ssc/external-net-id\\\":\\\"cfed230f-eb66-4f47-93ac-d3550a4774fa\\\",\\\"ssc/float-ip\\\":\\\"172.22.7.85\\\",\\\"ssc/network-id\\\":\\\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\\\",\\\"ssc/subnet-id\\\":\\\"c07fda56-a0dd-4717-9697-25294d558f91\\\"},\\\"labels\\\":{\\\"app\\\":\\\"svc-test3\\\",\\\"name\\\":\\\"svc\\\"},\\\"name\\\":\\\"svc-test\\\",\\\"namespace\\\":\\\"wdh\\\"},\\\"spec\\\":{\\\"ports\\\":[{\\\"name\\\":\\\"http2\\\",\\\"port\\\":878,\\\"targetPort\\\":3307},{\\\"name\\\":\\\"mysql-port\\\",\\\"port\\\":3306,\\\"targetPort\\\":3306}],\\\"selector\\\":{\\\"app\\\":\\\"svc-test\\\"},\\\"type\\\":\\\"NodePort\\\"}}\\n\"}},\"spec\":{\"$setElementOrder/ports\":[{\"port\":878},{\"port\":3306}],\"ports\":[{\"name\":\"http2\",\"port\":878,\"targetPort\":3307},{\"$patch\":\"delete\",\"port\":8878}]}}";
        String targetJson = "{\"metadata\":{\"uid\":\"d5e5da42-1913-4968-9b26-8c2fbd8506f5\",\"managedFields\":[{\"apiVersion\":\"v1\",\"manager\":\"kubectl-client-side-apply\",\"fieldsV1\":{\"f:metadata\":{\"f:annotations\":{\"f:ssc/external-net-id\":{},\"f:ssc/float-ip\":{},\"f:ssc/network-id\":{},\"f:kubectl.kubernetes.io/last-applied-configuration\":{},\".\":{},\"f:ssc/subnet-id\":{}},\"f:labels\":{\"f:app\":{},\"f:name\":{},\".\":{}}},\"f:spec\":{\"f:type\":{},\"f:selector\":{\"f:app\":{},\".\":{}},\"f:sessionAffinity\":{},\"f:externalTrafficPolicy\":{},\"f:ports\":{\"k:{\\\"port\\\":8878,\\\"protocol\\\":\\\"TCP\\\"}\":{\"f:port\":{},\"f:targetPort\":{},\"f:protocol\":{},\"f:name\":{},\".\":{}},\"k:{\\\"port\\\":3306,\\\"protocol\\\":\\\"TCP\\\"}\":{\"f:port\":{},\"f:targetPort\":{},\"f:protocol\":{},\"f:name\":{},\".\":{}},\".\":{}}}},\"time\":\"2022-01-11T08:49:42Z\",\"operation\":\"Update\",\"fieldsType\":\"FieldsV1\"}],\"resourceVersion\":\"18620455\",\"name\":\"svc-test\",\"namespace\":\"wdh\",\"creationTimestamp\":\"2022-01-11T08:49:42Z\",\"annotations\":{\"ssc/subnet-id\":\"c07fda56-a0dd-4717-9697-25294d558f91\",\"ssc/float-ip\":\"172.22.7.85\",\"ssc/external-net-id\":\"cfed230f-eb66-4f47-93ac-d3550a4774fa\",\"kubectl.kubernetes.io/last-applied-configuration\":\"{\\\"apiVersion\\\":\\\"v1\\\",\\\"kind\\\":\\\"Service\\\",\\\"metadata\\\":{\\\"annotations\\\":{\\\"ssc/external-net-id\\\":\\\"cfed230f-eb66-4f47-93ac-d3550a4774fa\\\",\\\"ssc/float-ip\\\":\\\"172.22.7.85\\\",\\\"ssc/network-id\\\":\\\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\\\",\\\"ssc/subnet-id\\\":\\\"c07fda56-a0dd-4717-9697-25294d558f91\\\"},\\\"labels\\\":{\\\"app\\\":\\\"svc-test3\\\",\\\"name\\\":\\\"svc\\\"},\\\"name\\\":\\\"svc-test\\\",\\\"namespace\\\":\\\"wdh\\\"},\\\"spec\\\":{\\\"ports\\\":[{\\\"name\\\":\\\"http2\\\",\\\"port\\\":8878,\\\"targetPort\\\":3307},{\\\"name\\\":\\\"mysql-port\\\",\\\"port\\\":3306,\\\"targetPort\\\":3306}],\\\"selector\\\":{\\\"app\\\":\\\"svc-test\\\"},\\\"type\\\":\\\"NodePort\\\"}}\\n\",\"ssc/network-id\":\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\"},\"selfLink\":\"/api/v1/namespaces/wdh/services/svc-test\",\"labels\":{\"app\":\"svc-test3\",\"name\":\"svc\"}},\"apiVersion\":\"v1\",\"kind\":\"Service\",\"spec\":{\"clusterIPs\":[\"172.22.7.85\"],\"sessionAffinity\":\"None\",\"selector\":{\"app\":\"svc-test\"},\"externalTrafficPolicy\":\"Cluster\",\"ports\":[{\"protocol\":\"TCP\",\"port\":8878,\"name\":\"http2\",\"targetPort\":3307,\"nodePort\":31082},{\"protocol\":\"TCP\",\"port\":3306,\"name\":\"mysql-port\",\"targetPort\":3306,\"nodePort\":61823}],\"type\":\"NodePort\",\"clusterIP\":\"172.22.7.85\"},\"status\":{\"loadBalancer\":{}}}";
        applyStrategicJsonPatchToJson(buffer, new ObjectMapper().readTree(targetJson));
    }
}
