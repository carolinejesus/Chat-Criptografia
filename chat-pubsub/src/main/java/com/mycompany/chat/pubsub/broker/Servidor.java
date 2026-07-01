package com.mycompany.chat.pubsub.broker;

import com.mycompany.chat.pubsub.security.ValidadorCertificados;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Classe principal: Valida se o broker possui cert autorizado,
 * abre porta do servidor, aceita conexões e cria uma thread ClientHandler
 * para cada cliente
 * - compartilhado entre todos os clientes
 *
 * @author caroline.jesus
 */
public class Servidor {
    public static void main(String[] args) throws Exception {
        if(!ValidadorCertificados.validarCertificadoBroker()){
            System.out.println("Servidor nao autorizado. Certificado do broker invalido.");
            return;
        }
        
        ServerSocket server = new ServerSocket(12345);
        Broker broker = new Broker();
        
        System.out.println("Servidor autorizado.");
        System.out.println("Servidor rodando...");
        
        while (true){
            Socket socket = server.accept();
            new Thread(new ClienteHandler(socket, broker)).start();
        }
    }
}
/**
 * O fluxo era pra ser: ca.cert contem a chave do professor
 * -> essa chave publica valida a assinatura do certificado do Broker
 * -> se a assinatura bater, broker é autorizado e roda
 */