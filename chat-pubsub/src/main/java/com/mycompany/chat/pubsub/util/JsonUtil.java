package com.mycompany.chat.pubsub.util;

import com.mycompany.chat.pubsub.model.Mensagem;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 * @author caroline.jesus
 */
public class JsonUtil {
    public static JSONObject parse(String texto) throws Exception {
        return (JSONObject) new JSONParser().parse(texto);
    }
    
    public static JSONObject mensagemToJson(Mensagem msg){
        JSONObject json = new JSONObject();
        json.put("type", "MESSAGE");
        json.put("topic", msg.getTopico());
        json.put("from", msg.getRemetente());
        json.put("message", msg.getConteudo());
        return json;
    }
}
