package com.mycompany.chat.pubsub.client;

/**
 *
 * @author Carol
 */
public interface MensagemListener {
    
   void aoReceberMensagem(String msg);
    
}
/**
 * permite que o ClienteConexao avise a TelaChat quando chegou uma mensahem do servidor
 * Fluxo:
 * Cliente conexao fica escutando -> chega uma mensagem -> clienteConexao chama Listener -> telaChat recebe a msg
 */