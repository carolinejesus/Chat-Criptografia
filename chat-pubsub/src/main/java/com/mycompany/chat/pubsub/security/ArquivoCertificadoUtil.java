package com.mycompany.chat.pubsub.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *Cria pasta de certificados, salva objetos serializados em arquivo, carrega arquivos serializados,
 * verifica se um arquivo existe.
 * 
 * armazena chaves privadas, certificados e chaves locais de topicos
 * @author Carol
 */
public class ArquivoCertificadoUtil {
    public static void criarPastaSeNaoExistir(String caminhoPasta){
        File pasta = new File(caminhoPasta);
        
        if(!pasta.exists()){
            pasta.mkdir();
        }
    }
    
    public static void salvarObjeto(String caminho, Object objeto) throws Exception{
        try (ObjectOutputStream saida = new ObjectOutputStream(new FileOutputStream(caminho))) {
            saida.writeObject(objeto);
        }
    }
    
    public static Object carregarObjeto (String caminho) throws Exception {
        try (ObjectInputStream entrada = new ObjectInputStream(new FileInputStream(caminho))) {
            return entrada.readObject();
        }
    }
    
    public static boolean arquivoExiste(String caminho){
        return new File(caminho).exists();
    }
}
