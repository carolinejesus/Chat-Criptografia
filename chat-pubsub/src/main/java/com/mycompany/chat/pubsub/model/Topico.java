package com.mycompany.chat.pubsub.model;

import com.mycompany.chat.pubsub.broker.ClienteHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author caroline.jesus
 */
public class Topico {
    private final String nome;
    private final Set<ClienteHandler> inscritos = ConcurrentHashMap.newKeySet();
    
    public Topico (String nome){
        this.nome = nome;
    }
    
    public void adicionar (ClienteHandler cliente){
        inscritos.add(cliente);
    }
    
    public void remover (ClienteHandler cliente){
        inscritos.remove(cliente);
    }
    
    public boolean vazio(){
        return inscritos.isEmpty();
    }
    
    public void broadcast(Mensagem msg){
        for (ClienteHandler c : inscritos){
            c.enviarMensagem(msg);
        }
    }
    
    public String getNome(){
        return nome;
    }
}
