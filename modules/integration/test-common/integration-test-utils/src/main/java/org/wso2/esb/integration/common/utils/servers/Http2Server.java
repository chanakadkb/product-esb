package org.wso2.esb.integration.common.utils.servers;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.esb.integration.common.utils.servers.http2.Http2ServerInitializer;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

/**
 * A HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 *
 * <p>This example is making use of the "multiplexing" http2 API, where streams are mapped to child
 * Channels. This API is very experimental and incomplete.
 */
public class Http2Server{

    private static final Log log=LogFactory.getLog(Http2Server.class);

    private boolean SSL = false;

    private int PORT=8080;
    EventLoopGroup group;

    public Http2Server(boolean SSL, int PORT) {

        this.PORT=PORT;
        this.SSL=SSL;
    }

    public void startServer(){
        SslContext sslCtx;
        if (SSL) {
            try {
                SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                        .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
                        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        .applicationProtocolConfig(new ApplicationProtocolConfig(
                                Protocol.ALPN,
                                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                                SelectorFailureBehavior.NO_ADVERTISE,
                                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                                SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2,
                                ApplicationProtocolNames.HTTP_1_1))
                        .build();

            }catch (SSLException|CertificateException e){
                sslCtx=null;
                log.error(e.getStackTrace());
            }

        } else {
            sslCtx = null;
        }
        group = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new Http2ServerInitializer(sslCtx));

            Channel ch = b.bind(PORT).sync().channel();

            // System.out.println("Chanel binded for the port:"+PORT);
            ch.closeFuture().sync();
            //  System.out.println("Closed chanel future");
        }catch(InterruptedException e){
            log.error("Server Interrupted"+e.getStackTrace());
        }

    }

    public void stopServer(){
        if(group!=null){
            group.shutdownGracefully();
        }
    }
}