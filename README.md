# yihttptunnel
A simple java implementation of a transparent HTTP proxy to unlock region lock for xiaoyi camera, which simply forward all "api.xiaoyi.com" traffic to another http proxy in the Mainland China. I can not config squid or other http proxy to do it correctly, that's the reason for this project.

Before running this proxy you have to
1. Set up a dns server with dnsmasq in your laptop, and config "api.xiaoyi.com" mapping to your laptop ip (google it if you don't know how to do :)).
2. Log in to your wifi router and change the dns server to your new dns server (your laptop ip).

Then you can start this transparent http proxy on your laptop, since this is a simple java project you can compile and run it with JDK 8,
```
cd src
javac com/yihttptunnel/*.java
sudo java com.yihttptunnel.Server
```

If you prefer a HTTP proxy in Mainland China other than the default one, you can run
```
sudo java com.yihttptunnel.Server [proxyhost] [proxyport]
```
Note, the HTTP proxy must support HTTPS.

You will have to set up the above again when the camera restarts.


--------------------------------------------------------------------

54.84.30.91
This is my own DNS server (and transparent HTTP proxy), which is working well for my camera. If you want to have a try you can simply specify the dns server with this IP on your wifi router.
Note, I can not guarantee any reliability since it's just a single instance on Amazon AWS.

