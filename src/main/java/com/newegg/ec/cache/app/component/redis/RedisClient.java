package com.newegg.ec.cache.app.component.redis;

import com.newegg.ec.cache.core.logger.CommonLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by Leo Fu on 2018/11/24.
 *
 * jedis�ͻ���û���ṩĳЩ�����API(��config rewrite��)����������RESPЭ���ʵ����Щ��API
 * ���伴�ã�����رտͻ�������
 */
public class RedisClient {

    public static final CommonLogger logger = new CommonLogger(RedisClient.class);

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    public RedisClient(String host, int port){
        try {

            this.socket = new Socket(host,port);
            this.outputStream = this.socket.getOutputStream();
            this.inputStream = this.socket.getInputStream();
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.setTcpNoDelay(true);
            socket.setSoLinger(true, 0);
            socket.connect(new InetSocketAddress(host, port), 3000);
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

        } catch (IOException e) {
            logger.error("Create RedisClient Error",e);
        }
    }

    /**
     * @param command
     * @return
     * @throws IOException
     */
    public String redisCommandOpt(String command) throws IOException {

        byte[] bytes= new byte[4096];
        outputStream.write(command.toString().getBytes());
        inputStream.read(bytes);
        return new String(bytes);

    }

    /**
     * ������֤redis����
     * @param pssword
     * @param command
     * @return
     * @throws IOException
     */
    public String redisCommandOpt(String pssword,String command) throws IOException {

        byte[] bytes= new byte[4096];
        StringBuilder auth = new StringBuilder();
        auth.append("*2").append("\r\n");
        auth.append("$4").append("\r\n");
        auth.append("AUTH").append("\r\n");
        auth.append("$").append(pssword.length()).append("\r\n");
        auth.append(pssword).append("\r\n");
        outputStream.write(auth.toString().getBytes());
        outputStream.write(command.toString().getBytes());
        inputStream.read(bytes);
        return new String(bytes);

    }

    public void closeClient() {

        try {
            if(socket != null && !socket.isClosed()){
                socket.close();
            }
            if(outputStream != null ){
                outputStream.close();
            }
            if(inputStream != null ){
                inputStream.close();
            }
        }catch (Exception e){
            logger.error("Close RedisClient Error",e);
        }

    }


}
