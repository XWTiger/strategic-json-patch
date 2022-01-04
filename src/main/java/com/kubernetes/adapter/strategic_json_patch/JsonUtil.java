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

   /* public static void main(String[] args) throws JsonProcessingException {
        String buffer = "{\n" +
                "  \"spec\": {\n" +
                "    \"$setElementOrder/ports\": [\n" +
                "      {\n" +
                "        \"port\": 3306\n" +
                "      },\n" +
                "      {\n" +
                "        \"port\": 82\n" +
                "      }\n" +
                "    ],\n" +
                "    \"ports\": [\n" +
                "      {\n" +
                "        \"name\": \"http3\",\n" +
                "        \"nodePort\": 30404,\n" +
                "        \"port\": 82,\n" +
                "        \"protocol\": \"TCP\",\n" +
                "        \"targetPort\": 82\n" +
                "      },\n" +
                "      {\n" +
                "        \"$patch\": \"delete\",\n" +
                "        \"port\": 81\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        String targetJson = "{\n" +
                "  \"metadata\": {\n" +
                "    \"uid\": \"30ae5ed3-ea52-44c5-afa0-5e0f67b2d1ef\",\n" +
                "    \"managedFields\": [\n" +
                "      {\n" +
                "        \"apiVersion\": \"v1\",\n" +
                "        \"manager\": \"kubectl-client-side-apply\",\n" +
                "        \"fieldsV1\": {\n" +
                "          \"f:metadata\": {\n" +
                "            \"f:annotations\": {\n" +
                "              \"f:ssc/external-net-id\": {},\n" +
                "              \"f:ssc/network-id\": {},\n" +
                "              \"f:kubectl.kubernetes.io/last-applied-configuration\": {},\n" +
                "              \".\": {},\n" +
                "              \"f:ssc/subnet-id\": {}\n" +
                "            },\n" +
                "            \"f:labels\": {\n" +
                "              \"f:app\": {},\n" +
                "              \".\": {}\n" +
                "            }\n" +
                "          },\n" +
                "          \"f:spec\": {\n" +
                "            \"f:type\": {},\n" +
                "            \"f:selector\": {\n" +
                "              \"f:app\": {},\n" +
                "              \".\": {}\n" +
                "            },\n" +
                "            \"f:sessionAffinity\": {},\n" +
                "            \"f:externalTrafficPolicy\": {},\n" +
                "            \"f:ports\": {\n" +
                "              \"k:{\\\"port\\\":81,\\\"protocol\\\":\\\"TCP\\\"}\": {\n" +
                "                \"f:port\": {},\n" +
                "                \"f:targetPort\": {},\n" +
                "                \"f:protocol\": {},\n" +
                "                \"f:name\": {},\n" +
                "                \".\": {}\n" +
                "              },\n" +
                "              \"k:{\\\"port\\\":3306,\\\"protocol\\\":\\\"TCP\\\"}\": {\n" +
                "                \"f:port\": {},\n" +
                "                \"f:targetPort\": {},\n" +
                "                \"f:protocol\": {},\n" +
                "                \"f:name\": {},\n" +
                "                \".\": {}\n" +
                "              },\n" +
                "              \".\": {}\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"time\": \"2021-12-30T09:52:51Z\",\n" +
                "        \"operation\": \"Update\",\n" +
                "        \"fieldsType\": \"FieldsV1\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"resourceVersion\": \"1049575\",\n" +
                "    \"name\": \"svc-test\",\n" +
                "    \"namespace\": \"wdh\",\n" +
                "    \"creationTimestamp\": \"2021-12-30T09:52:51Z\",\n" +
                "    \"annotations\": {\n" +
                "      \"ssc/subnet-id\": \"c07fda56-a0dd-4717-9697-25294d558f91\",\n" +
                "      \"ssc/external-net-id\": \"cfed230f-eb66-4f47-93ac-d3550a4774fa\",\n" +
                "      \"kubectl.kubernetes.io/last-applied-configuration\": \"{\\\"apiVersion\\\":\\\"v1\\\",\\\"kind\\\":\\\"Service\\\",\\\"metadata\\\":{\\\"annotations\\\":{\\\"ssc/external-net-id\\\":\\\"cfed230f-eb66-4f47-93ac-d3550a4774fa\\\",\\\"ssc/network-id\\\":\\\"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\\\",\\\"ssc/subnet-id\\\":\\\"c07fda56-a0dd-4717-9697-25294d558f91\\\"},\\\"labels\\\":{\\\"app\\\":\\\"svc-test\\\"},\\\"name\\\":\\\"svc-test\\\",\\\"namespace\\\":\\\"wdh\\\"},\\\"spec\\\":{\\\"ports\\\":[{\\\"name\\\":\\\"http\\\",\\\"nodePort\\\":30526,\\\"port\\\":3306,\\\"targetPort\\\":3306},{\\\"name\\\":\\\"http2\\\",\\\"nodePort\\\":30525,\\\"port\\\":81,\\\"targetPort\\\":81}],\\\"selector\\\":{\\\"app\\\":\\\"svc-test\\\"},\\\"type\\\":\\\"NodePort\\\"}}\\n\",\n" +
                "      \"ssc/network-id\": \"acf6d425-6449-4d1a-b91a-f5fbf0bbd18f\"\n" +
                "    },\n" +
                "    \"selfLink\": \"/api/v1/namespaces/wdh/services/svc-test\",\n" +
                "    \"labels\": {\n" +
                "      \"app\": \"svc-test\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"apiVersion\": \"v1\",\n" +
                "  \"kind\": \"Service\",\n" +
                "  \"spec\": {\n" +
                "    \"clusterIPs\": [\n" +
                "      \"172.22.7.117\"\n" +
                "    ],\n" +
                "    \"sessionAffinity\": \"None\",\n" +
                "    \"selector\": {\n" +
                "      \"app\": \"svc-test\"\n" +
                "    },\n" +
                "    \"externalTrafficPolicy\": \"Cluster\",\n" +
                "    \"ports\": [\n" +
                "      {\n" +
                "        \"protocol\": \"TCP\",\n" +
                "        \"port\": 3306,\n" +
                "        \"name\": \"http\",\n" +
                "        \"targetPort\": 3306,\n" +
                "        \"nodePort\": 30526\n" +
                "      },\n" +
                "      {\n" +
                "        \"protocol\": \"TCP\",\n" +
                "        \"port\": 81,\n" +
                "        \"name\": \"http2\",\n" +
                "        \"targetPort\": 81,\n" +
                "        \"nodePort\": 30525\n" +
                "      }\n" +
                "    ],\n" +
                "    \"type\": \"NodePort\",\n" +
                "    \"clusterIP\": \"172.22.7.117\"\n" +
                "  },\n" +
                "  \"status\": {\n" +
                "    \"loadBalancer\": {}\n" +
                "  }\n" +
                "}";
        applyStrategicJsonPatchToJsonPatch(buffer, new ObjectMapper().readTree(targetJson));
    }*/
}
