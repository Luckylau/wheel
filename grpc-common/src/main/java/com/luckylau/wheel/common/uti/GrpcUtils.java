package com.luckylau.wheel.common.uti;

import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.luckylau.wheel.common.Constants;
import com.luckylau.wheel.common.PayloadRegistry;
import com.luckylau.wheel.common.exception.DeserializationException;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.exception.RemoteException;
import com.luckylau.wheel.common.exception.SerializationException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestMeta;
import com.luckylau.wheel.common.response.Response;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcUtils {
    static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Object to json string.
     *
     * @param obj obj
     * @return json string
     * @throws SerializationException if transfer failed
     */
    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new SerializationException(obj.getClass(), e);
        }
    }

    /**
     * Json string deserialize to Object.
     *
     * @param json json string
     * @param cls  class of object
     * @param <T>  General type
     * @return object
     * @throws DeserializationException if deserialize failed
     */
    public static <T> T toObj(String json, Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (IOException e) {
            throw new DeserializationException(cls, e);
        }
    }

    /**
     * convert request to payload.
     *
     * @param request request.
     * @param meta    request meta.
     * @return payload.
     */
    public static Payload convert(Request request, RequestMeta meta) {
        //meta.
        Payload.Builder payloadBuilder = Payload.newBuilder();
        Metadata.Builder metaBuilder = Metadata.newBuilder();
        if (meta != null) {
            metaBuilder.putAllHeaders(request.getHeaders()).setType(request.getClass().getSimpleName());
        }
        metaBuilder.setClientIp(NetUtils.localIP());
        payloadBuilder.setMetadata(metaBuilder.build());

        // request body .
        request.clearHeaders();
        String jsonString = toJson(request);
        return payloadBuilder
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(jsonString, Charset.forName(Constants.ENCODE))))
                .build();

    }

    /**
     * convert request to payload.
     *
     * @param request request.
     * @return payload.
     */
    public static Payload convert(Request request) {

        Metadata newMeta = Metadata.newBuilder().setType(request.getClass().getSimpleName())
                .setClientIp(NetUtils.localIP()).putAllHeaders(request.getHeaders()).build();
        request.clearHeaders();
        String jsonString = toJson(request);

        Payload.Builder builder = Payload.newBuilder();

        return builder
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(jsonString, Charset.forName(Constants.ENCODE))))
                .setMetadata(newMeta).build();

    }

    /**
     * convert response to payload.
     *
     * @param response response.
     * @return payload.
     */
    public static Payload convert(Response response) {
        String jsonString = toJson(response);

        Metadata.Builder metaBuilder = Metadata.newBuilder().setType(response.getClass().getSimpleName());
        return Payload.newBuilder()
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(jsonString, Charset.forName(Constants.ENCODE))))
                .setMetadata(metaBuilder.build()).build();
    }

    /**
     * parse payload to request/response model.
     *
     * @param payload payload to be parsed.
     * @return payload
     */
    public static Object parse(Payload payload) {
        Class classType = PayloadRegistry.getClassByType(payload.getMetadata().getType());
        if (classType != null) {
            Object obj = toObj(payload.getBody().getValue().toString(Charset.forName(Constants.ENCODE)), classType);
            if (obj instanceof Request) {
                ((Request) obj).putAllHeader(payload.getMetadata().getHeadersMap());
            }
            return obj;
        } else {
            throw new RemoteException(GrpcException.SERVER_ERROR,
                    "Unknown payload type:" + payload.getMetadata().getType());
        }

    }

    public static class PlainRequest {

        String type;

        Object body;

        /**
         * Getter method for property <tt>type</tt>.
         *
         * @return property value of type
         */
        public String getType() {
            return type;
        }

        /**
         * Setter method for property <tt>type</tt>.
         *
         * @param type value to be assigned to property type
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Getter method for property <tt>body</tt>.
         *
         * @return property value of body
         */
        public Object getBody() {
            return body;
        }

        /**
         * Setter method for property <tt>body</tt>.
         *
         * @param body value to be assigned to property body
         */
        public void setBody(Object body) {
            this.body = body;
        }
    }
}
