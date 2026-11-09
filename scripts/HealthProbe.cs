// Probe local con pin del certificado de ESTA instancia. No cambia validacion TLS global.
using System;
using System.IO;
using System.Net;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
public static class SigerLocalHealth {
    public static string Read(string address,string store,string password) {
        Uri uri=new Uri(address);
        if(uri.Scheme!="https"||uri.Host!="localhost"||uri.AbsolutePath!="/api/health")throw new InvalidOperationException("Sonda limitada a localhost.");
        using(var expected=new X509Certificate2(store,password,X509KeyStorageFlags.EphemeralKeySet)) {
            byte[] raw=expected.RawData;
            var request=(HttpWebRequest)WebRequest.Create(uri);
            request.Timeout=4000;request.ReadWriteTimeout=4000;request.AllowAutoRedirect=false;request.Proxy=null;
            request.ServerCertificateValidationCallback=(sender,cert,chain,errors)=>{
                if(cert==null)return false;
                byte[] actual=cert.GetRawCertData();
                if(actual.Length!=raw.Length)return false;
                int difference=0;for(int i=0;i<raw.Length;i++)difference|=actual[i]^raw[i];
                return difference==0 && DateTime.Now>=expected.NotBefore && DateTime.Now<=expected.NotAfter;
            };
            using(var response=(HttpWebResponse)request.GetResponse())
            using(var reader=new StreamReader(response.GetResponseStream()))return reader.ReadToEnd();
        }
    }
}
