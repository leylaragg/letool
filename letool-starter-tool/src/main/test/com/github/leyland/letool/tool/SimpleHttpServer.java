package com.github.leyland.letool.tool;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @ClassName <h2>SimpleHttpServer</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
public class SimpleHttpServer {

    public static void main(String[] args) {
        try {
            // 启动服务器，监听 8080 端口
            ServerSocket serverSocket = new ServerSocket(8080);
            System.out.println("Server is listening on port 8080...");

            // 接受客户端连接
            Socket socket = serverSocket.accept();
            InputStream inputStream = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);  // 输出客户端请求
                if (line.isEmpty()) break;  // 请求头结束 Http请求和报文中间有空行
            }


            // 发送 HTTP 响应
            OutputStream outputStream = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(outputStream, true);
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: text/plain");
            writer.println();
            writer.println("Hello from Simple HTTP Server!");

            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void test() {
        char[] buffer = new char[1024];
        System.out.println(buffer.length);
    }
}
