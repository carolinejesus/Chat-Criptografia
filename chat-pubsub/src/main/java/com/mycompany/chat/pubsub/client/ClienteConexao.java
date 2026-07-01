package com.mycompany.chat.pubsub.client;

import com.mycompany.chat.pubsub.security.ArquivoCertificadoUtil;
import com.mycompany.chat.pubsub.security.CertificadoCliente;
import com.mycompany.chat.pubsub.security.CryptoUtil;
import com.mycompany.chat.pubsub.security.ValidadorCertificados;
import com.mycompany.chat.pubsub.util.JsonUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Abre conexoes socket com o broker, registra usuarios, faz login, envia
 * comandos JSON, escuta respostas, guarda certificados e chave privada do
 * cliente, guarda chaves de topicos localmente e envia as msg criptografadas
 *
 * as chaves ficam somente do lado do cliente, broker nao tem acesso
 *
 * @author Carol
 */
public class ClienteConexao {

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter saida;
    private MensagemListener listener;
    private final List<String> mensagemBuffer = new ArrayList<>();
    private String usuarioAtual = null;
    private CertificadoCliente certificadoCliente;
    private final Map<String, String> chavesTopicos = new ConcurrentHashMap<>();
    private final Map<String, Integer> versoesChavesTopicos = new ConcurrentHashMap<>();
    private PrivateKey chavePrivadaCliente;

    public void conectar(String nome) throws Exception {
        socket = new Socket("localhost", 12345);

        entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));

        saida = new PrintWriter(
                socket.getOutputStream(),
                true);

        this.usuarioAtual = nome;

        certificadoCliente = (CertificadoCliente) ArquivoCertificadoUtil.carregarObjeto(getPastaCertificados() + "/" + nome + ".cert");
        chavePrivadaCliente = (PrivateKey) ArquivoCertificadoUtil.carregarObjeto(getPastaCertificados() + "/" + nome + "_private.key");

        if (!certificadoCliente.getNomeUsuario().equals(nome)) {
            throw new Exception("O certificado pertence a outra pessoa.");
        }

        if (!ValidadorCertificados.validarCertificadoUsuario(certificadoCliente)) {
            throw new Exception("Certificado do cliente invalido.");
        }
        JSONObject jsonAuth = new JSONObject();
        jsonAuth.put("type", "AUTH");
        jsonAuth.put("nome", certificadoCliente.getNomeUsuario());
        jsonAuth.put("chavePublica", certificadoCliente.getChavePublicaUsuarioBase64());
        jsonAuth.put("assinaturaBroker", certificadoCliente.getAssinaturaBrokerBase64());
        saida.println(jsonAuth.toJSONString());
        System.out.println("AUTH enviado:" + jsonAuth.toJSONString());
        String resposta = entrada.readLine();
        System.out.println("Resposta do servidor no login: " + resposta);
        if (!"AUTH_OK".equals(resposta)) {
            throw new Exception("Falha no login: " + resposta);
        }
        carregarChavesT();
    }

    public void registrar(String nome) throws Exception {
        socket = new Socket("localhost", 12345);
        entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        saida = new PrintWriter(socket.getOutputStream(), true);
        String pasta = getPastaCertificados();
        ArquivoCertificadoUtil.criarPastaSeNaoExistir(pasta);
        KeyPair chavesCliente = CryptoUtil.gerarParChaves();
        String chavePublicaBase64 = CryptoUtil.chavePublicaParaBase64(chavesCliente.getPublic());

        JSONObject json = new JSONObject();
        json.put("type", "REGISTER");
        json.put("nome", nome);
        json.put("chavePublica", chavePublicaBase64);

        saida.println(json.toJSONString());
        String resposta = entrada.readLine();
        System.out.println("Resposta do servidor no registro: " + resposta);

        if (resposta == null) {
            throw new Exception("Servidor fechou a conexao sem responder");
        }

        JSONObject jsonResposta = JsonUtil.parse(resposta);
        String tipo = (String) jsonResposta.get("type");

        if (!"REGISTER_OK".equals(tipo)) {
            throw new Exception("Resposta inesperada do serividor: " + resposta);
        }
        String nomeUsuario = (String) jsonResposta.get("nome");
        String chavePublicaUsuario = (String) jsonResposta.get("chavePublica");
        String assinaturaBroker = (String) jsonResposta.get("assinaturaBroker");
        CertificadoCliente certificado = new CertificadoCliente(nomeUsuario, chavePublicaUsuario, assinaturaBroker);
        ArquivoCertificadoUtil.salvarObjeto(pasta + "/" + nomeUsuario + "_private.key", chavesCliente.getPrivate());
        ArquivoCertificadoUtil.salvarObjeto(pasta + "/" + nomeUsuario + ".cert", certificado);
        socket.close();
    }

    public void iniciarEscuta() {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = entrada.readLine()) != null) {
                    if (listener != null) {
                        listener.aoReceberMensagem(msg);
                    } else {
                        mensagemBuffer.add(msg);
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexao encerrada.");
            }
        }).start();
    }

    public void setMensagemListener(MensagemListener listener) {
        this.listener = listener;
        for (String msg : mensagemBuffer) {
            this.listener.aoReceberMensagem(msg);
        }
        mensagemBuffer.clear();
    }

    public void criarTopico(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "CREATE_TOPIC");
        json.put("topic", topico);
        saida.println(json.toJSONString());
        try {
            String chaveAES = CryptoUtil.gerarChaveAESBase64();
            salvarChaveTopico(topico, chaveAES, 1);

            System.out.println("Chave AES criada para " + topico);
        } catch (Exception e) {
            System.out.println("Erro ao gerar chave aes");
            e.printStackTrace();
        }
    }

    public void entrarTopico(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "SUBSCRIBE");
        json.put("topic", topico);
        saida.println(json.toJSONString());
    }

    public void usarTopicos(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "USE");
        json.put("topic", topico);
        saida.println(json.toJSONString());
    }

    public void sairTopico(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "UNSUBSCRIBE");
        json.put("topic", topico);
        saida.println(json.toJSONString());
    }

    public void desconectar() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "DISCONNECT");
            if (saida != null) {
                saida.println(json.toJSONString());
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }

    public void enviarMensagemCriptografada(String mensagemCriptografada, Integer versao) {
        JSONObject json = new JSONObject();
        json.put("type", "PUBLISH");
        json.put("message", mensagemCriptografada);
        json.put("encrypted", true);
        json.put("keyVersion", versao);
        saida.println(json.toJSONString());
    }

    public void enviarPacoteChaveTopico(String topico, String usuarioDestino, int versao, String chaveCriptografada) {
        JSONObject json = new JSONObject();
        json.put("type", "TOPIC_KEY_PACKAGE");
        json.put("topic", topico);
        json.put("to", usuarioDestino);
        json.put("keyVersion", versao);
        json.put("encryptedKey", chaveCriptografada);
        saida.println(json.toJSONString());
        System.out.println("Pacote de chave enviado");
    }

    public void solicitarTopicos() {
        JSONObject json = new JSONObject();
        json.put("type", "LIST_TOPICS");
        System.out.println("Solicitando topicos...");
        saida.println(json.toJSONString());
    }

    public void solicitarParticipantes(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "LIST_PARTICIPANTS");
        json.put("topic", topico);
        System.out.println("Solicitando participantes do topico " + topico);
        saida.println(json.toJSONString());
    }

    public void solicitarSolicitacoes(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "LIST_ACCESS_REQUESTS");
        json.put("topic", topico);
        saida.println(json.toJSONString());
    }

    public void baixarMensagens(String topico) {
        JSONObject json = new JSONObject();
        json.put("type", "DOWNLOAD_MESSAGES");
        //baixa o historico completo de um topico quando abre o topico
        //historico completo de um topico quando abrir o topico se ja estava participando
        json.put("topic", topico);
        saida.println(json.toJSONString());
    }

    public void sincronizarMensagensPendentes() {
        JSONObject json = new JSONObject();

        json.put("type", "SYNC_MESSAGES");
        //baixa mensagens pendentes do usuario ao conectar -- 
        //mensagens sincronizadas no login
        saida.println(json.toJSONString());
    }

    public void aprovarEntrada(String topicoAberto, String usuario) {
        JSONObject json = new JSONObject();
        json.put("type", "APPROVE_TOPIC_ACCESS");
        json.put("topic", topicoAberto);
        json.put("user", usuario);
        saida.println(json.toJSONString());
    }

    public void recusarEntrada(String topicoAberto, String usuario) {
        JSONObject json = new JSONObject();
        json.put("type", "REJECT_TOPIC_ACCESS");
        json.put("topic", topicoAberto);
        json.put("user", usuario);
        saida.println(json.toJSONString());
    }

    public void salvarChaveTopico(String topico, String chaveAESBase64, int versao) {
        chavesTopicos.put(topico, chaveAESBase64);
        versoesChavesTopicos.put(topico, versao);
        System.out.println("Chave do topico  versao " + versao);
        salvarChavesTopicoEmArquivo();
    }

    private void salvarChavesTopicoEmArquivo() {
        try {
            Map<String, Object> dados = new HashMap<>();
            dados.put("chaves", new HashMap<>(chavesTopicos));
            dados.put("versoes", new HashMap<>(versoesChavesTopicos));
            ArquivoCertificadoUtil.salvarObjeto(getArquivoChavesTopicos(), dados);
            System.out.println("chaves salvas");
        } catch (Exception e) {
            System.out.println("erro ao salvar");
            e.printStackTrace();
        }
    }

    public void carregarChavesT() {
        try {
            File arquivo = new File(getArquivoChavesTopicos());
            if (!arquivo.exists()) {
                System.out.println("Nenhum arquivo de chaves de tópicos encontrado para " + usuarioAtual);
                return;
            }
            Map<String, Object> dados = (Map<String, Object>) ArquivoCertificadoUtil.carregarObjeto(getArquivoChavesTopicos());
            Map<String, String> chaves = (Map<String, String>) dados.get("chaves");
            Map<String, Integer> versoes = (Map<String, Integer>) dados.get("versoes");
            chavesTopicos.clear();
            versoesChavesTopicos.clear();
            if (chaves != null) {
                chavesTopicos.putAll(chaves);
            }
            if (versoes != null) {
                versoesChavesTopicos.putAll(versoes);
            }
            System.out.println("Chaves dos tópicos carregadas para " + usuarioAtual);
            System.out.println("Tópicos com chave: " + chavesTopicos.keySet());
        } catch (Exception e) {
            System.out.println("Erro ao carregar chaves dos tópicos.");
            e.printStackTrace();
        }
    }

    public boolean possuiChaveTopico(String topico) {
        return chavesTopicos.containsKey(topico);
    }

    public String getChaveTopico(String topico) {
        return chavesTopicos.get(topico);
    }

    public Integer getVersaoChaveTopico(String topico) {
        return versoesChavesTopicos.get(topico);
    }

    private String getPastaCertificados() {
        return System.getProperty("user.dir") + "/certificados";
    }

    private String getArquivoChavesTopicos() {
        return getPastaCertificados() + "/" + usuarioAtual + "_topic_keys.dat";
    }

    public String getUsuarioAtual() {
        return usuarioAtual;
    }

    public PrivateKey getChavePrivadaCliente() {
        return chavePrivadaCliente;
    }

    public CertificadoCliente getCertificadoCliente() {
        return certificadoCliente;
    }

    public String formatarMensagem(String texto) {
        try {
            JSONObject json = (JSONObject) new JSONParser().parse(texto);
            String tipo = (String) json.get("type");
            if ("MESSAGE".equals(tipo)) {
                String topico = (String) json.get("topic");
                String remetente = (String) json.get("from");
                String mensagem = (String) json.get("message");
                return "[" + topico + "] " + remetente + ": " + mensagem;
            }
            return texto;
        } catch (ParseException e) {
            return texto;
        }
    }
}
