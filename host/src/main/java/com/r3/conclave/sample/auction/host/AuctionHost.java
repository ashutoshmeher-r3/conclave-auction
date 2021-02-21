package com.r3.conclave.sample.auction.host;

import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.host.AttestationParameters;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.MailCommand;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class AuctionHost {

    private static final String ENCLAVE_CLASS_NAME =
            "com.r3.conclave.sample.auction.enclave.AuctionEnclave";
    private EnclaveHost enclaveHost;
    private Map<String, Socket> clientMap = new HashMap<>();

    public static void main(String[] args) throws EnclaveLoadException {
        AuctionHost host = new AuctionHost();
        host.verifyPlatformSupport();
        host.initializeEnclave();
        host.startServer();
    }

    private void startServer(){
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(5051);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }
        System.out.println("Listening on port 5051");
        while (true) {
            try {
                assert serverSocket != null;
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                System.out.println("I/O error: " + e);
            }
            String routingHint = UUID.randomUUID().toString();
            clientMap.put(routingHint, clientSocket);

            final EnclaveInstanceInfo attestation = enclaveHost.getEnclaveInstanceInfo();
            final byte[] attestationBytes = attestation.serialize();
            sendMessageToClient(routingHint, attestationBytes);
            recieveMailFromClientAndDeliverToEnclave(clientSocket, routingHint);
        }
    }

    private void sendMessageToClient(String routingHint, byte[] content){
        try {
            Socket clientSocket = clientMap.get(routingHint);
            DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());
            outputStream.writeInt(content.length);
            outputStream.write(content);
            outputStream.flush();
        }catch (IOException ioe){
            ioe.printStackTrace();
            return;
        }
    }


    private void recieveMailFromClientAndDeliverToEnclave(Socket clientSocket, String routingHint){
        try {
            DataInputStream input = new DataInputStream(clientSocket.getInputStream());
            byte[] mailBytes = new byte[input.readInt()];
            input.readFully(mailBytes);

            enclaveHost.deliverMail(1, mailBytes, routingHint);
        }catch (IOException ioException){
            ioException.printStackTrace();
        }
    }

    private  void verifyPlatformSupport(){
        try {
            EnclaveHost.checkPlatformSupportsEnclaves(true);
            System.out.println("This platform supports enclaves in simulation, debug and release mode.");
        } catch (EnclaveLoadException e) {
            System.out.println("This platform does not support hardware enclaves: " + e.getMessage());
        }
    }

    private void initializeEnclave() throws EnclaveLoadException{
        enclaveHost = EnclaveHost.load(ENCLAVE_CLASS_NAME);

//        enclaveHost.start(
//            new AttestationParameters.DCAP(),
//            new EnclaveHost.MailCallbacks() {
//                @Override
//                public void postMail(byte[] encryptedBytes, String routingHint) {
//                    sendMessageToClient(routingHint, encryptedBytes);
//                }
//
//            });
        enclaveHost.start(
            new AttestationParameters.DCAP(), mailCommands -> {
                    for (MailCommand command : mailCommands) {
                        if (command instanceof MailCommand.PostMail) {
                            String routingHint = ((MailCommand.PostMail) command).getRoutingHint();
                            byte[] content = ((MailCommand.PostMail) command).getEncryptedBytes();
                            sendMessageToClient(routingHint, content);
                        }
                    }
                }
        );
    }

}
