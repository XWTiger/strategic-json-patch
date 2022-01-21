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
     *
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
                            String descField = orderArray.get(0).fieldNames().next();
                            //get des filed, like $setElementOrder/ports
                            String[] buffer = field.split("/");
                            JsonNode desNode = strategicNode.get(buffer[1]);
                            ArrayNode arrayNode = (ArrayNode) desNode;

                            JsonNode target = findValueByPath((String) maps.get("path"), targetNode);
                            ArrayNode orderedArray = JsonNodeFactory.instance.arrayNode();// to order the new arr
                            for (JsonNode orderNode : orderArray) {
                                int index = 0;
                                //check exist from  strategic and  exist from target node

                                if (null == arrayNode || arrayNode.size() <= 0) {
                                    ArrayNode fromTarget = (ArrayNode) target.get(buffer[1]);
                                    for (JsonNode childNode : fromTarget) {
                                        if (childNode.get(descField).asText().equals(orderNode.get(descField).asText())) {
                                            orderedArray.add(childNode.deepCopy());
                                            break;
                                        }
                                    }
                                    continue;
                                }
                                for (JsonNode node : arrayNode) {

                                    if (Objects.nonNull(node.get("$patch"))) {
                                        //delete do nothing
                                        if (orderNode.get(descField).asText().equals(node.get(descField).asText())) {
                                            arrayNode.remove(index);
                                            break;
                                        } else  { // the order node exist in  target
                                            //add
                                            ArrayNode tArray = (ArrayNode) target.get(buffer[1]);
                                            if (index >= tArray.size()) {
                                                break;
                                            }
                                            JsonNode tnode = findNodeFromTarget(tArray, orderNode, descField);
                                            if (Objects.nonNull(tnode)) {
                                                //replace
                                                JsonNode replaced = JsonNodeFactory.instance.objectNode();
                                                /*copyFromTo(node, tnode);*/
                                                copyFromTo(tnode, replaced);
                                                orderedArray.add(replaced);

                                            } else {
                                                //add
                                                tArray.add(node);
                                                orderedArray.add(node);
                                            }
                                            break;
                                        }

                                    } else if (orderNode.get(descField).asText().equals(node.get(descField).asText())) {
                                        //add or replace
                                        ArrayNode tArray = (ArrayNode) target.get(buffer[1]);
                                        if (index >= tArray.size()) {
                                            break;
                                        }
                                        JsonNode tnode = findNodeFromTarget(tArray, node, descField);
                                        if (Objects.nonNull(tnode)) {
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
                                        arrayNode.remove(index);
                                        break;
                                    } else {
                                        //the node exist in target node
                                        ArrayNode fromTarget = (ArrayNode) target.get(buffer[1]);
                                        boolean findOne = false;
                                        for (JsonNode childNode : fromTarget) {
                                            if (childNode.get(descField).asText().equals(orderNode.get(descField).asText())) {
                                                orderedArray.add(childNode.deepCopy());
                                                findOne = true;
                                                break;
                                            }
                                        }
                                        if (findOne) {
                                            break;
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

    public static JsonNode findNodeFromTarget(ArrayNode targetArr, JsonNode fromNode, String field) {
        for (JsonNode jsonNode : targetArr) {
            if (jsonNode.get(field).asText().equals(fromNode.get(field).asText())) {
                return jsonNode;
            }
        }
        return null;
    }

    public static void copyFromTo(JsonNode from, JsonNode to) {
        Iterator<String> iterator = from.fieldNames();
        ObjectNode objectNode = (ObjectNode) to;
        while (iterator.hasNext()) {
            String filed = iterator.next();
            if (Objects.nonNull(from.get(filed)) && !"null".equals(from.get(filed).toString())) {
                objectNode.put(filed, from.get(filed));
            } else {
                objectNode.remove(filed);
            }

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

        String mergeString = "{\"spec\":{\"$setElementOrder/ports\":[{\"port\":80},{\"port\":3306}],\"ports\":[{\"name\":\"http\",\"port\":80,\"targetPort\":3306}]}}";
        mergeString = "{\"spec\":{\"$setElementOrder/ports\":[{\"port\":11},{\"port\":80},{\"port\":3306}],\"ports\":[{\"name\":\"ww\",\"port\":11}]}}";
        String originString = "{\"metadata\":{\"uid\":\"06fea961-4fd8-4d20-bb05-673a925628ba\",\"managedFields\":[{\"apiVersion\":\"v1\",\"manager\":\"kubectl-client-side-apply\",\"fieldsV1\":{\"f:metadata\":{\"f:annotations\":{\"f:ssc/external-net-id\":{},\"f:ssc/float-ip\":{},\"f:ssc/network-id\":{},\"f:kubectl.kubernetes.io/last-applied-configuration\":{},\".\":{},\"f:ssc/subnet-id\":{}},\"f:labels\":{\"f:app\":{},\"f:name\":{},\".\":{}}},\"f:spec\":{\"f:type\":{},\"f:selector\":{\"f:app\":{},\".\":{}},\"f:sessionAffinity\":{},\"f:externalTrafficPolicy\":{},\"f:ports\":{\"k:{\\\"port\\\":80,\\\"protocol\\\":\\\"TCP\\\"}\":{\"f:port\":{},\"f:targetPort\":{},\"f:protocol\":{},\"f:name\":{},\".\":{}},\"k:{\\\"port\\\":3306,\\\"protocol\\\":\\\"TCP\\\"}\":{\"f:port\":{},\"f:targetPort\":{},\"f:protocol\":{},\"f:name\":{},\".\":{}},\".\":{}}}},\"time\":\"2022-01-20T08:50:38Z\",\"operation\":\"Update\",\"fieldsType\":\"FieldsV1\"}],\"resourceVersion\":\"31682543\",\"name\":\"svc-test3\",\"namespace\":\"wdh\",\"creationTimestamp\":\"2022-01-20T08:50:38Z\",\"annotations\":{\"ssc/subnet-id\":\"c07fda56-a0dd-4717-9697-25294d558f91\",\"ssc/float-ip\":\"172.22.7.95\",\"ssc/external-net-id\":\"cfed230f-eb66-4f47-93ac-d3550a4774fa\",\"kubectl.kubernetes.io/last-applied-configuration\":\"{\\\"apiVersion\\\":\\\"v1\\\",\\\"kind\\\":\\\"Service\\\",\\\"metadata\\\":{\\\"annotations\\\":{\\\"ssc/external-net-id\\\":\\\"cfed230f-eb66-4f47-93ac-d3550a4774fa\\\",\\\"ssc/float-ip\\\":\\\"172.22.7.95\\\",\\\"ssc/network-id\\\":\\\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\\\",\\\"ssc/subnet-id\\\":\\\"c07fda56-a0dd-4717-9697-25294d558f91\\\"},\\\"labels\\\":{\\\"app\\\":\\\"svc-test3\\\",\\\"name\\\":\\\"svc\\\"},\\\"name\\\":\\\"svc-test3\\\",\\\"namespace\\\":\\\"wdh\\\"},\\\"spec\\\":{\\\"ports\\\":[{\\\"name\\\":\\\"http\\\",\\\"port\\\":80,\\\"targetPort\\\":3306},{\\\"name\\\":\\\"mysql-port\\\",\\\"port\\\":3306,\\\"targetPort\\\":3306}],\\\"selector\\\":{\\\"app\\\":\\\"svc-test\\\"},\\\"type\\\":\\\"NodePort\\\"}}\\n\",\"ssc/network-id\":\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\"},\"selfLink\":\"/api/v1/namespaces/wdh/services/svc-test3\",\"labels\":{\"app\":\"svc-test3\",\"name\":\"svc\"}},\"apiVersion\":\"v1\",\"kind\":\"Service\",\"spec\":{\"clusterIPs\":[\"172.22.7.95\"],\"sessionAffinity\":\"None\",\"selector\":{\"app\":\"svc-test\"},\"externalTrafficPolicy\":\"Cluster\",\"ports\":[{\"protocol\":\"TCP\",\"port\":80,\"name\":\"http\",\"targetPort\":3306,\"nodePort\":42072},{\"protocol\":\"TCP\",\"port\":3306,\"name\":\"mysql-port\",\"targetPort\":3306,\"nodePort\":38557}],\"type\":\"NodePort\",\"clusterIP\":\"172.22.7.95\"},\"status\":{\"loadBalancer\":{}}}";
        mergeString = "{\"spec\":{\"$setElementOrder/ports\":[{\"port\":80},{\"port\":3307}],\"ports\":[{\"name\":\"mysql-port\",\"nodePort\":31967,\"port\":3307,\"protocol\":\"TCP\",\"targetPort\":3306},{\"$patch\":\"delete\",\"port\":3306}]}}";
        applyStrategicJsonPatchToJson(mergeString, new ObjectMapper().readTree(originString));
    }
}
