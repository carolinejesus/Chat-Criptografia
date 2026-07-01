package com.mycompany.chat.pubsub.model;

/**
 *
 * @author caroline.jesus
 */
public class Mensagem {
    private final String topico;
    private final String remetente;
    private final String conteudo;
    
    public Mensagem(String topico, String remetente, String conteudo) {
        this.topico = topico;
        this.remetente = remetente;
        this.conteudo = conteudo;
    }

    public String getTopico() {
        return topico;
    }

    public String getRemetente() {
        return remetente;
    }

    public String getConteudo() {
        return conteudo;
    }
}
