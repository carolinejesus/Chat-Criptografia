package com.mycompany.chat.pubsub.broker;

import com.mycompany.chat.pubsub.model.Mensagem;
import com.mycompany.chat.pubsub.model.Usuario;
import com.mycompany.chat.pubsub.security.ArquivoCertificadoUtil;
import com.mycompany.chat.pubsub.security.CertificadoBroker;
import com.mycompany.chat.pubsub.security.CryptoUtil;
import com.mycompany.chat.pubsub.security.ValidadorCertificados;
import com.mycompany.chat.pubsub.util.JsonUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * recebe msgs do json, valida registros de auth, chama o broker, envia
 * respostas para o cliente, mantem o topico atual do cliente, encerra a conexao
 * qnd o cliente da logout
 * 
 * nao armazena regra principal do sistema funcionando como intermediario entre cliente e broker
 *
 * @author caroline.jesus
 */
public class ClienteHandler implements Runnable {
    private final Socket socket;
    private final Broker broker;
    private Usuario usuario;
    private PrintWriter saida;
    private BufferedReader entrada;
    private String topicoAtual;

    public ClienteHandler(Socket socket, Broker broker) {
        this.socket = socket;
        this.broker = broker;
    }

    //fluxo principal
    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida = new PrintWriter(socket.getOutputStream(), true);
            String primeiraLinha = entrada.readLine();

            if (primeiraLinha == null) {
                socket.close();
                return;
            }

            JSONObject primeiroJson = JsonUtil.parse(primeiraLinha);
            String tipoInicial = (String) primeiroJson.get("type");

            System.out.println("Tipo inicial: " + tipoInicial);

            if ("REGISTER".equals(tipoInicial)) {
                registrarCliente(primeiroJson);
                socket.close();
                return;
            }

            if ("AUTH".equals(tipoInicial)) {
                if (!autenticarPorCertificado(primeiroJson)) {
                    socket.close();
                    return;
                }

                broker.registrarCliente(usuario.getNome(), this);
                broker.notificarAtualizacao();
                broker.entregarPacotesDeChavesPendentes(usuario.getNome(), this);
            } else {
                saida.println("Erro: comando inicial invalido.");
                socket.close();
                return;
            }

            String linha;

            while ((linha = entrada.readLine()) != null) {
                JSONObject json = JsonUtil.parse(linha);
                String tipo = (String) json.get("type");

                if (tipo == null) {
                    saida.println("Erro: comando sem tipo.");
                    continue;
                }

                switch (tipo) {
                    case "CREATE_TOPIC": {
                        String topico = (String) json.get("topic");

                        if (topico == null || topico.isBlank()) {
                            saida.println("Erro: nome do topico invalido.");
                            break;
                        }

                        boolean criado = broker.criarTopicoComCriador(topico, usuario.getNome());

                        if (!criado) {
                            saida.println("nao foi possivel criar o topico");
                            break;
                        }

                        topicoAtual = topico;
                        saida.println("Topico criado: " + topico);
                        saida.println("Agora voce esta usando o topico: " + topico);
                        break;
                    }

                    case "SUBSCRIBE": {
                        String topico = (String) json.get("topic");

                        if (topico == null || topico.isBlank()) {
                            saida.println("Erro: nome do topico invalido.");
                            break;
                        }
                        solicitarEntrada(topico);
                        break;
                    }

                    case "USE": {
                        String topico = (String) json.get("topic");

                        if (topico == null || topico.isBlank()) {
                            saida.println("Erro: nome do topico invalido.");
                            break;
                        }
                        usar(topico);
                        break;
                    }

                    case "UNSUBSCRIBE": {
                        String topico = (String) json.get("topic");

                        if (topico == null || topico.isBlank()) {
                            saida.println("Erro: nome do topico invalido.");
                            break;
                        }
                        desinscrever(topico);
                        break;
                    }

                    case "PUBLISH": {
                        String mensagem = (String) json.get("message");

                        if (mensagem == null || mensagem.isBlank()) {
                            saida.println("Erro: mensagem vazia.");
                            break;
                        }
                        publicar(mensagem);
                        break;
                    }

                    case "LIST_TOPICS": {
                        listarTopicos();
                        break;
                    }

                    case "DISCONNECT": {
                        System.out.println(usuario.getNome() + "solicitou desconexao");
                        return;
                    }

                    case "DOWNLOAD_MESSAGES": {
                        String topico = (String) json.get("topic");

                        if (topico == null || topico.isBlank()) {
                            saida.println("Erro: topico invalido");
                            break;
                        }

                        baixarMensagens(topico);
                        break;
                    }

                    case "SYNC_MESSAGES": {
                        sincronizarMensagens();
                        break;
                    }

                    case "LIST_PARTICIPANTS": {
                        String topico = (String) json.get("topic");
                        System.out.println("servidos recebeu pedido de participantes do topico " + topico);

                        if (topico == null || topico.isBlank()) {
                            saida.println("Erro: topico invalido.");
                            break;
                        }
                        listarParticipantes(topico);
                        break;
                    }

                    case "LIST_ACCESS_REQUESTS": {
                        String topico = (String) json.get("topic");

                        if (topico == null || topico.isBlank()) {
                            saida.println("topico invalido");
                            break;
                        }
                        broker.notificarSolicitacoesTopico(topico);
                        break;
                    }

                    case "APPROVE_TOPIC_ACCESS": {
                        String topico = (String) json.get("topic");
                        String usuarioSolicitante = (String) json.get("user");

                        if (topico == null || topico.isBlank() || usuarioSolicitante == null || usuarioSolicitante.isBlank()) {
                            saida.println("Dados invalidos");
                            break;
                        }
                        aprovarEntrada(topico, usuarioSolicitante);
                        break;
                    }

                    case "REJECT_TOPIC_ACCESS": {
                        String topico = (String) json.get("topic");
                        String usuarioSolicitante = (String) json.get("user");

                        if (topico == null || topico.isBlank() || usuarioSolicitante == null || usuarioSolicitante.isBlank()) {
                            saida.println("Dados invalidos");
                            break;
                        }
                        recusarEntrada(topico, usuarioSolicitante);
                        break;
                    }

                    case "TOPIC_KEY_PACKAGE": {
                        repassarPacoteChave(json);
                        break;
                    }
                    default:
                        saida.println("Erro: tipo de comando desconhecido: " + tipo);
                        break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (usuario != null) {
                broker.removerCliente(usuario.getNome());
            }
            try {
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*autentica o cliente
    o cliente envia nome, chavep, assinatura do broker, o servidor valida se o
    certificado eh confiavel, se a for os dados sao nome + ":" + chavepublica*/
    private boolean autenticarPorCertificado(JSONObject jsonAuth) {
        try {
            String tipo = (String) jsonAuth.get("type");

            if (!"AUTH".equals(tipo)) {
                saida.println("Erro: autenticacao esperada.");
                return false;
            }

            String nome = (String) jsonAuth.get("nome");
            String chavePublicaCliente = (String) jsonAuth.get("chavePublica");
            String assinaturaBroker = (String) jsonAuth.get("assinaturaBroker");

            if (nome == null || nome.isBlank() || chavePublicaCliente == null || chavePublicaCliente.isBlank() || assinaturaBroker == null || assinaturaBroker.isBlank()) {
                saida.println("Erro: dados de autenticacao incompletos.");
                return false;
            }

            if (!ValidadorCertificados.validarCertificadoBroker()) {
                saida.println("Erro: certificado invalido.");
                return false;
            }

            String pasta = System.getProperty("user.dir") + "/certificados";
            CertificadoBroker certificadoBroker = (CertificadoBroker) ArquivoCertificadoUtil.carregarObjeto(pasta + "/broker.cert");
            PublicKey chavePublicaBroker = CryptoUtil.base64ParaChavePublica(certificadoBroker.getChavePublicaBrokerBase64());
            String dadosCertificado = nome + ":" + chavePublicaCliente;
            boolean certificadoValidado = CryptoUtil.verificarAssinatura(dadosCertificado, assinaturaBroker, chavePublicaBroker);
            System.out.println("Certificado valido? " + certificadoValidado);

            if (!certificadoValidado) {
                saida.println("Erro: certificado invalido.");
                return false;
            }

            usuario = new Usuario(nome);
            broker.registrarChavePublicaUsuario(nome, chavePublicaCliente);
            saida.println("AUTH_OK");
            System.out.println("Cliente autenticado por certificado: " + nome);
            return true;
        } catch (Exception e) {
            System.out.println("Erro durante autenticacao por certificado.");
            e.printStackTrace();
            return false;
        }
    }

    /*recebe pedido de registro do cliente
    o cliente envia nome + chave publica
    o broker assina esses dados com sua chave privada e devolve um cert logico*/
    private void registrarCliente(JSONObject jsonRegistro) {
        try {
            String nome = (String) jsonRegistro.get("nome");
            String chavePublicaCliente = (String) jsonRegistro.get("chavePublica");

            if (nome == null || nome.isBlank() || chavePublicaCliente == null || chavePublicaCliente.isBlank()) {
                saida.println("{\"type\":\"REGISTER_ERROR\",\"message\":\"Dados incompletos\"}");
                return;
            }

            broker.registrarChavePublicaUsuario(nome, chavePublicaCliente);
            String pasta = System.getProperty("user.dir") + "/certificados";
            PrivateKey chavePrivadaBroker = (PrivateKey) ArquivoCertificadoUtil.carregarObjeto(pasta + "/broker_private.key");
            String dadosCertificado = nome + ":" + chavePublicaCliente;
            String assinaturaBroker = CryptoUtil.assinar(dadosCertificado, chavePrivadaBroker);
            JSONObject resposta = new JSONObject();
            resposta.put("type", "REGISTER_OK");
            resposta.put("nome", nome);
            resposta.put("chavePublica", chavePublicaCliente);
            resposta.put("assinaturaBroker", assinaturaBroker);
            saida.println(resposta.toJSONString());
        } catch (Exception e) {
            e.printStackTrace();
            JSONObject erro = new JSONObject();
            erro.put("type", "REGISTER_ERROR");
            erro.put("message", "Falha ao registrar cliente.");
            saida.println(erro.toJSONString());
        }
    }

    //operacoes principais de toicos e msgs
    private void usar(String nomeTopico) {
        if (!broker.usuarioEstaInscrito(usuario.getNome(), nomeTopico)) {
            saida.println("Erro: voce nao esta inscrito nesse topico.");
            return;
        }
        topicoAtual = nomeTopico;
        saida.println("Agora voce esta usando o topico: " + topicoAtual);
    }

    private void publicar(String conteudo) {
        if (topicoAtual == null) {
            saida.println("Erro: nenhum topico ativo.");
            return;
        }
        if (!broker.usuarioEstaInscrito(usuario.getNome(), topicoAtual)) {
            saida.println("Erro: Voce nao esta inscrito nesse topico.");
            return;
        }
        Mensagem msg = new Mensagem(topicoAtual, usuario.getNome(), conteudo);

        broker.enviarMensagem(msg);
    }

    private void desinscrever(String nomeTopico) {
        if (!broker.usuarioEstaInscrito(usuario.getNome(), nomeTopico)) {
            saida.println("Erro: voce nao esta inscrito nesse topico.");
            return;
        }
        broker.desinscreverUsuario(usuario.getNome(), nomeTopico);
        if (nomeTopico.equals(topicoAtual)) {
            topicoAtual = null;
        }
        saida.println("Voce saiu do topico: " + nomeTopico);
    }

    private void solicitarEntrada(String topico) {
        boolean solicitado = broker.solicitarEntradaTopico(usuario.getNome(), topico);

        if (!solicitado) {
            saida.println("Erro: nao foi possivel solicitar entrada.");
            return;
        }

        saida.println("Solicitacao enviada");
    }

    private void aprovarEntrada(String topico, String usuarioSolicitante) {
        boolean aprovado = broker.aprovarEntradaTopico(usuario.getNome(), usuarioSolicitante, topico);

        if (!aprovado) {
            saida.println("nao foi possivel aprovar");
            return;
        }
        saida.println("Entrada aprovada no topico " + topico);
    }

    private void recusarEntrada(String topico, String usuarioSolicitante) {
        boolean recusado = broker.recusarEntrada(usuario.getNome(), usuarioSolicitante, topico);

        if (!recusado) {
            saida.println("nao foi possivel recusar");
            return;
        }
        saida.println("Entrada no topico " + topico + " recusada.");
    }

    private void repassarPacoteChave(JSONObject json) {
        String topico = (String) json.get("topic");
        String destino = (String) json.get("to");
        Object versao = json.get("keyVersion");
        String chaveCriptografada = (String) json.get("encryptedKey");

        if (topico == null || topico.isBlank() || destino == null || destino.isBlank()
                || chaveCriptografada == null || chaveCriptografada.isBlank() || versao == null) {
            saida.println("pacote invalido");
            return;
        }
        if (!broker.usuarioEstaInscrito(usuario.getNome(), topico)) {
            saida.println("Erro voce nao ta inscrito no topico");
            return;
        }
        if (!broker.usuarioEstaInscrito(destino, topico)) {
            saida.println("Erro: usuario destino nao esta inscrito nesse topico.");
            return;
        }
        ClienteHandler clienteDestino = broker.getClienteOnline(destino);
        if (clienteDestino == null) {
            saida.println(clienteDestino + "nao ta online");
        }
        JSONObject pacote = new JSONObject();
        pacote.put("type", "TOPIC_KEY_PACKAGE");
        pacote.put("topic", topico);
        pacote.put("from", usuario.getNome());
        pacote.put("keyVersion", versao);
        pacote.put("encryptedKey", chaveCriptografada);
        broker.repassarPacoteChave(destino, pacote);
    }

    private void baixarMensagens(String topico) {
        if (!broker.usuarioEstaInscrito(usuario.getNome(), topico)) {
            saida.println("Erro: voce nao esta inscrito nesse topico.");
            return;
        }
        JSONArray mensagensArray = new JSONArray();
        for (Mensagem msg : broker.getHistoricoTopicoParaUsuario(usuario.getNome(), topico)) {
            JSONObject jsonMsg = JsonUtil.mensagemToJson(msg);
            mensagensArray.add(jsonMsg);
        }
        JSONObject resposta = new JSONObject();
        resposta.put("type", "MESSAGE_HISTORY");
        resposta.put("topic", topico);
        resposta.put("messages", mensagensArray);
        saida.println(resposta.toJSONString());
    }
    
    private void sincronizarMensagens() {
        broker.entregarMensagensPendentes(usuario.getNome(), this);
    }
    
    private void listarParticipantes(String topico) {
        if (!broker.usuarioEstaInscrito(usuario.getNome(), topico)) {
            saida.println("Erro: voce nao esta inscrito nesse topico.");
            return;
        }
        JSONArray listaParticipantes = new JSONArray();
        for (String participante : broker.listarParticipantes(topico)) {
            listaParticipantes.add(participante);
        }
        JSONObject resposta = new JSONObject();
        resposta.put("type", "PARTICIPANT_LIST");
        resposta.put("topic", topico);
        resposta.put("participants", listaParticipantes);
        System.out.println("enviando participantes " + resposta.toJSONString());
        saida.println(resposta.toJSONString());
    }
    
    private void listarTopicos() {
        JSONObject json = new JSONObject();
        org.json.simple.JSONArray arrayTopicos = new org.json.simple.JSONArray();

        for (String nomeTopico : broker.listarTopicos()) {
            arrayTopicos.add(nomeTopico);
        }
        json.put("type", "TOPIC_LIST");
        json.put("topics", arrayTopicos);
        System.out.println(json.toJSONString());
        saida.println(json.toJSONString());
    }
    
    public boolean enviarMensagem(Mensagem msg) {
        JSONObject json = JsonUtil.mensagemToJson(msg);
        saida.println(json.toJSONString());
        return !saida.checkError();
    }    

    public boolean enviarJson(JSONObject json) {
        saida.println(json.toJSONString());
        return !saida.checkError();
    }
}
