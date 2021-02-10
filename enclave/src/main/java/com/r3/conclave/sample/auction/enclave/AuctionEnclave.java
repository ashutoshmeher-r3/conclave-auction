package com.r3.conclave.sample.auction.enclave;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;
import com.r3.conclave.sample.auction.common.Message;
import com.r3.conclave.sample.auction.common.MessageSerializer;
import com.r3.conclave.shaded.kotlin.Pair;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class AuctionEnclave extends Enclave {

    private Map<String, PublicKey> userKeysMap = new HashMap<>();
    private Map<String, Integer> userBidsMap = new HashMap<>();
    private Pair<String, PublicKey> auctionAdmin = null;

    // Mails send from clients to the enclave are received here
    @Override
    protected void receiveMail(long id, String userRoute, EnclaveMail mail) {
        Message message = readMail(mail);
        PublicKey senderPK = mail.getAuthenticatedSender();
        if (message.getType().equals("BID")) {
            userBidsMap.put(userRoute, message.getBid());
            userKeysMap.put(userRoute, senderPK);
        } else if (message.getType().equals("PROCESS-BID")) {
            auctionAdmin = new Pair<>(userRoute, senderPK);
            processBids();
        }
    }

    // Process user bids. The highest bidder wins.
    private void processBids() {
        String winner = null;
        int maxBid = 0;
        for (String userRoute : userBidsMap.keySet()) {
            Integer bid = userBidsMap.get(userRoute);

            if (bid > maxBid) {
                maxBid = bid;
                winner = userRoute;
            }
        }

        sendAuctionResult(winner);
    }

    // Send auction result back to the client
    private void sendAuctionResult(String winner){

        for(String userRoute: userKeysMap.keySet()){
            if(userRoute.equals(winner)){
                sendMail(userKeysMap.get(userRoute), userRoute, "Congratulations! Your made the winning bid");
            }else{
                sendMail(userKeysMap.get(userRoute), userRoute, "Better Luck Next Time!");
            }
        }

        sendMail(auctionAdmin.getSecond(), auctionAdmin.getFirst(), "The winning bid is: " + userBidsMap.get(winner));

    }

    private void sendMail(PublicKey key, String routingHint, String message) {
        MutableMail mail = createMail(key, message.getBytes());
        postMail(mail, routingHint);
    }


    private Message readMail(EnclaveMail mail) {
        Kryo kryo = new Kryo();
        kryo.register(Message.class, new MessageSerializer());
        Input input = new Input(new ByteArrayInputStream(mail.getBodyAsBytes()));
        return kryo.readObject(input, Message.class);
    }

}
