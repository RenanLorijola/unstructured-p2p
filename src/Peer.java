import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class Peer {
    static private final Integer ARRAY_CAPACIDADE = 9999;
    static private final Integer TIMEOUT_SEGUNDOS = 10;

    static private InetAddress ip;
    static private Integer porta;
    static private InetAddress[] peersIps = new InetAddress[2];
    static private Integer[] peersPortas = new Integer[2];
    static private String caminhoArquivo;
    static private DatagramSocket socket;
    static private ArrayBlockingQueue<String> listaArquivos = new ArrayBlockingQueue<String>(ARRAY_CAPACIDADE);
    static private ArrayBlockingQueue<String> searchEnviados = new ArrayBlockingQueue<String>(ARRAY_CAPACIDADE);
    static private ArrayBlockingQueue<String> responseEnviados = new ArrayBlockingQueue<String>(ARRAY_CAPACIDADE);
    static private ArrayBlockingQueue<String> searchReenviados = new ArrayBlockingQueue<String>(ARRAY_CAPACIDADE);
    static private ArrayBlockingQueue<String> responseRecebidos = new ArrayBlockingQueue<String>(ARRAY_CAPACIDADE);

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        while(true){
            System.out.println("----------------------------------------------------------------------");
            System.out.println("Escolha entre INICIALIZA ou SEARCH");
            System.out.println("----------------------------------------------------------------------");
            String funcao = scanner.nextLine();
            switch (funcao.toUpperCase(Locale.ROOT)){
                case "INICIALIZA":
                    // Evita o metodo INICIALIZA caso o peer ja tenha sido inicializado
                    if(ip != null){
                        System.out.println("Peer já inicializado, tente usar \"SEARCH\"");
                        break;
                    }

                    // Inicializa o peer, capturando o ip e porta do peer, de onde seus arquivos estão localizados e de dois peers conhecidos
                    System.out.println("Digite ip e porta no formato ip:porta do peer:");
                    String[] ipPorta = scanner.nextLine().split(":");
                    ip = InetAddress.getByName(ipPorta[0]);
                    porta = Integer.parseInt(ipPorta[1]);

                    socket = new DatagramSocket(porta);

                    System.out.println("Digite o local da pasta onde seus arquivos estão armazenados:");
                    caminhoArquivo = scanner.nextLine();

                    System.out.println("Digite o ip:porta de outros dois peers, um por linha:");
                    ipPorta = scanner.nextLine().split(":");
                    peersIps[0] = InetAddress.getByName(ipPorta[0]);
                    peersPortas[0] = Integer.parseInt(ipPorta[1]);

                    ipPorta = scanner.nextLine().split(":");
                    peersIps[1] = InetAddress.getByName(ipPorta[0]);
                    peersPortas[1] = Integer.parseInt(ipPorta[1]);

                    DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(caminhoArquivo));
                    StringBuilder mensagemArquivos = new StringBuilder("arquivos da pasta:");
                    stream.forEach(file -> {
                        listaArquivos.add(file.getFileName().toString());
                        mensagemArquivos.append(" " + file.getFileName());
                    });
                    System.out.println(mensagemArquivos);

                    // Inicializa a thread de monitoração de pasta
                    ThreadMonitoracao threadMonitoracao = new ThreadMonitoracao();
                    threadMonitoracao.start();

                    // Inicializa a thread para escutar requisições
                    ThreadEscutaRequisicoes threadEscutaRequisicoes = new ThreadEscutaRequisicoes();
                    threadEscutaRequisicoes.start();
                    break;
                case "SEARCH":
                    // Evita o metodo SEARCH caso o peer não tenha sido inicializado
                    if(ip == null){
                        System.out.println("Peer já inicializado, tente usar \"SEARCH\"");
                        break;
                    }

                    System.out.println("Digite o nome do arquivo com a extensão que está procurando:");
                    String arquivoProcurado = scanner.nextLine();

                    // Inicializa a thread de envio de pacote com a mensagem SEARCH
                    ThreadEnviaSearch threadEnviaSearch = new ThreadEnviaSearch(arquivoProcurado);
                    threadEnviaSearch.start();
                    break;
                default:
                    System.out.println("Opção não mapeada, use \"INICIALIZA\" OU \"SEARCH\"");
                    break;
            }
        }
    }

    public static class ThreadMonitoracao extends Thread {
        @Override
        public void run(){
            try {
                while(true){
                    // espera 30 segundos para fazer a monitoração
                    this.sleep(30 * 1000);
                    DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(caminhoArquivo));
                    StringBuilder stringBuilder = new StringBuilder("Sou peer " + ipPortaToString(ip,porta) + " com arquivos");
                    ArrayBlockingQueue novaListaArquivos = new ArrayBlockingQueue(ARRAY_CAPACIDADE);
                    stream.forEach(file -> {
                        novaListaArquivos.add(file.getFileName().toString());
                        stringBuilder.append(" " + file.getFileName());
                    });
                    listaArquivos = novaListaArquivos;
                    System.out.println(stringBuilder);
                }
            } catch (Exception e) {
                System.out.println("ocorreu um erro ao ler a lista de arquivos");
            }
        }
    }

    public static class ThreadEscutaRequisicoes extends Thread {

        @Override
        public void run(){
            try {
                while(true){
                    byte[] recBuffer = new byte[2048];
                    DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
                    socket.receive(recPkt);
                    String mensagemRecebidaJSON = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
                    Gson gson = new Gson();
                    Mensagem mensagemRecebida = gson.fromJson(mensagemRecebidaJSON, Mensagem.class);

                    if(mensagemRecebida.getTipo().equals("SEARCH")){
                        new ThreadLidaComMensagemSearch(mensagemRecebida).start();
                    } else {
                        String mensagemId = mensagemRecebida.getId();
                        if(searchEnviados.contains(mensagemId) && !responseRecebidos.contains(mensagemId)){
                            responseRecebidos.add(mensagemId);
                            System.out.println("peer com arquivo procurado: " + ipPortaToString(mensagemRecebida.getIpOrigem(),mensagemRecebida.getPortaOrigem()) + " " + mensagemRecebida.getArquivo());
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("ocorreu um erro ouvir requisições de outros peers");
            }
        }
    }

    public static class ThreadEnviaSearch extends Thread {

        private String arquivoProcurado;

        public ThreadEnviaSearch(String arquivoProcurado){
            this.arquivoProcurado = arquivoProcurado;
        }

        @Override
        public void run(){
            try {
                Gson gson = new Gson();
                Random gerador = new Random();
                Integer peerEscolhido = gerador.nextInt(2);

                Mensagem mensagem = new Mensagem(arquivoProcurado, ip, porta);
                String responseString = gson.toJson(mensagem);

                byte[] sendBuffer;
                sendBuffer = responseString.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, peersIps[peerEscolhido], peersPortas[peerEscolhido]);
                socket.send(sendPacket);
                searchEnviados.add(mensagem.getId());
                this.sleep(TIMEOUT_SEGUNDOS * 1000);
                if(!responseRecebidos.contains(mensagem.getId())){
                    responseRecebidos.add(mensagem.getId());
                    System.out.println("ninguém no sistema possui o arquivo " + arquivoProcurado);
                }
            } catch (IOException | InterruptedException e) {
                System.out.println("Erro ao enviar pacote para o peer aleatorio");
            }

        }
    }

    public static class ThreadLidaComMensagemSearch extends Thread {
        private Mensagem mensagemRecebida;

        public ThreadLidaComMensagemSearch(Mensagem mensagemRecebida){
            this.mensagemRecebida = mensagemRecebida;
        }

        @Override
        public void run(){
            try {
                Gson gson = new Gson();
                String mensagemId = mensagemRecebida.getId();
                if(!searchEnviados.contains(mensagemId) && !responseEnviados.contains(mensagemId) && !searchReenviados.contains(mensagemId)){
                    if(listaArquivos.contains(mensagemRecebida.getArquivo())){
                        // cria uma mensagem de resposta e devolve ao peer requisitante do search
                        Mensagem mensagem = new Mensagem(mensagemRecebida.getArquivo(), ip, porta, mensagemId);
                        String responseString = gson.toJson(mensagem);

                        byte[] sendBuffer;
                        sendBuffer = responseString.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, mensagemRecebida.getIpOrigem(), mensagemRecebida.getPortaOrigem());
                        socket.send(sendPacket);
                        responseEnviados.add(mensagem.getId());
                        System.out.println("tenho " + mensagem.getArquivo() + " respondendo para " + ipPortaToString(mensagemRecebida.getIpOrigem(), mensagemRecebida.getPortaOrigem()));
                    } else {
                        // pega a mensage recebida e reencaminha para um peer aleatorio
                        Random gerador = new Random();
                        Integer peerEscolhido = gerador.nextInt(2);

                        String responseString = gson.toJson(mensagemRecebida);

                        byte[] sendBuffer;
                        sendBuffer = responseString.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, peersIps[peerEscolhido], peersPortas[peerEscolhido]);
                        socket.send(sendPacket);
                        searchReenviados.add(mensagemRecebida.getId());
                        System.out.println("não tenho " + mensagemRecebida.getArquivo() + " encaminhando para " + ipPortaToString(peersIps[peerEscolhido], peersPortas[peerEscolhido]));
                    }
                } else if (responseEnviados.contains(mensagemId)){
                    System.out.println("Requisição já processada para " + mensagemRecebida.getArquivo());
                }
            } catch (Exception e){
                System.out.println("Ocorreu um erro ao enviar a resposta de um search recebido");
            }
        }
    }

    public static String ipPortaToString(InetAddress ip, Integer porta){
        return ip.toString().substring(1) + ":" + porta;
    }
}
