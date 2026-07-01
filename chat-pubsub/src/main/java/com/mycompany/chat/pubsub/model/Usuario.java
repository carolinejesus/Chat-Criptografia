package com.mycompany.chat.pubsub.model;

/**
 *
 * @author caroline.jesus
 */
public class Usuario {
    private final String nome;
    
    public Usuario (String nome) {
        this.nome = nome;
    }
    
    public String getNome(){
        return nome;
    }
}
