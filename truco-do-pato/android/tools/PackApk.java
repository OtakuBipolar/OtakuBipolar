import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Última etapa do build: junta o classes.dex no APK que o aapt2 gerou, alinha os arquivos
 * sem compressão em 4 bytes (o Android 11+ exige isso do resources.arsc), assina com o
 * esquema v2 e confere a assinatura. O v1 (assinatura de JAR) fica de fora: o app exige
 * Android 7.0+, que já verifica o v2, e o v1 do apksig 2.3.0 não roda em JDK novo.
 *
 * Uso: PackApk <base.apk> <classes.dex> <keystore.p12> <alias> <saida.apk> <minSdk>
 * A senha do keystore vem da variável de ambiente KEYSTORE_PASS.
 */
public final class PackApk {
    private static final int ALIGNMENT = 4;
    private static final int ALIGNMENT_EXTRA_ID = 0xd935; // mesmo campo que o zipalign/apksig usam
    private static final long FIXED_TIME = 1199145600000L; // 2008-01-01, igual ao aapt2: build reproduzível

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.err.println("uso: PackApk <base.apk> <classes.dex> <keystore.p12> <alias> <saida.apk> <minSdk>");
            System.exit(2);
        }
        File base = new File(args[0]);
        File dex = new File(args[1]);
        File keystore = new File(args[2]);
        String alias = args[3];
        File out = new File(args[4]);
        int minSdk = Integer.parseInt(args[5]);
        String pass = System.getenv("KEYSTORE_PASS");
        if (pass == null || pass.isEmpty()) throw new IllegalStateException("defina KEYSTORE_PASS");

        File unsigned = new File(out.getPath() + ".unsigned");
        merge(base, dex, unsigned);
        checkAlignment(unsigned);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystore)) {
            ks.load(in, pass.toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(alias, pass.toCharArray());
        if (key == null) throw new IllegalStateException("chave '" + alias + "' não encontrada em " + keystore);
        List<X509Certificate> certs = new ArrayList<>();
        for (Certificate c : ks.getCertificateChain(alias)) certs.add((X509Certificate) c);

        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("TRUCO", key, certs).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(unsigned)
                .setOutputApk(out)
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setCreatedBy("Truco do Pato build")
                .build()
                .sign();
        Files.delete(unsigned.toPath());

        checkAlignment(out);
        ApkVerifier.Result result = new ApkVerifier.Builder(out).build().verify();
        for (ApkVerifier.IssueWithParams w : result.getWarnings()) System.err.println("aviso: " + w);
        for (ApkVerifier.IssueWithParams e : result.getErrors()) System.err.println("erro: " + e);
        if (!result.isVerified() || !result.isVerifiedUsingV2Scheme()) {
            throw new IllegalStateException("assinatura do APK não passou na verificação");
        }
        System.out.println("assinado e verificado (esquema v2) -> " + out + " (" + out.length() / 1024 + " KB)");
    }

    /** Copia as entradas do APK base e acrescenta o classes.dex, alinhando as que vão sem compressão. */
    private static void merge(File base, File dex, File out) throws IOException {
        try (ZipFile in = new ZipFile(base);
             CountingOutputStream counter = new CountingOutputStream(new FileOutputStream(out));
             ZipOutputStream zip = new ZipOutputStream(counter)) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || e.getName().equals("classes.dex")) continue;
                try (InputStream data = in.getInputStream(e)) {
                    write(zip, counter, e.getName(), readAll(data), e.getMethod() == ZipEntry.STORED);
                }
            }
            write(zip, counter, "classes.dex", Files.readAllBytes(dex.toPath()), false);
        }
    }

    private static void write(ZipOutputStream zip, CountingOutputStream counter, String name, byte[] data,
                              boolean stored) throws IOException {
        ZipEntry out = new ZipEntry(name);
        out.setTime(FIXED_TIME);
        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(data);
            out.setMethod(ZipEntry.STORED);
            out.setSize(data.length);
            out.setCompressedSize(data.length);
            out.setCrc(crc.getValue());
            // Cabeçalho local = 30 bytes + nome + extra. O extra leva o enchimento que alinha os dados.
            long dataStart = counter.count + 30 + name.getBytes(StandardCharsets.UTF_8).length + 6;
            int pad = (int) ((ALIGNMENT - dataStart % ALIGNMENT) % ALIGNMENT);
            byte[] extra = new byte[6 + pad];
            extra[0] = (byte) (ALIGNMENT_EXTRA_ID & 0xff);
            extra[1] = (byte) (ALIGNMENT_EXTRA_ID >>> 8);
            extra[2] = (byte) (2 + pad);
            extra[3] = 0;
            extra[4] = (byte) ALIGNMENT;
            extra[5] = 0;
            out.setExtra(extra);
        } else {
            out.setMethod(ZipEntry.DEFLATED);
        }
        zip.putNextEntry(out);
        zip.write(data);
        zip.closeEntry();
    }

    /** Percorre o diretório central do zip e falha se algum arquivo sem compressão estiver desalinhado. */
    private static void checkAlignment(File apk) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(apk, "r")) {
            // O registro de fim do diretório central fica nos últimos 22 bytes (+ comentário, que não usamos).
            long eocd = -1;
            byte[] tail = new byte[(int) Math.min(f.length(), 22 + 65535)];
            f.seek(f.length() - tail.length);
            f.readFully(tail);
            for (int i = tail.length - 22; i >= 0; i--) {
                if (le32(tail, i) == 0x06054b50) {
                    eocd = f.length() - tail.length + i;
                    break;
                }
            }
            if (eocd < 0) throw new IllegalStateException("zip sem diretório central: " + apk);
            byte[] end = new byte[22];
            f.seek(eocd);
            f.readFully(end);
            int count = le16(end, 10);
            long cd = le32(end, 16) & 0xffffffffL;

            int stored = 0;
            byte[] c = new byte[46];
            byte[] l = new byte[30];
            for (int i = 0; i < count; i++) {
                f.seek(cd);
                f.readFully(c);
                if (le32(c, 0) != 0x02014b50) throw new IllegalStateException("diretório central corrompido");
                int method = le16(c, 10);
                int nameLen = le16(c, 28);
                int extraLen = le16(c, 30);
                int commentLen = le16(c, 32);
                long local = le32(c, 42) & 0xffffffffL;
                byte[] name = new byte[nameLen];
                f.readFully(name);
                cd += 46 + nameLen + extraLen + commentLen;
                if (method != 0) continue;
                f.seek(local);
                f.readFully(l);
                long dataStart = local + 30 + le16(l, 26) + le16(l, 28);
                stored++;
                if (dataStart % ALIGNMENT != 0) {
                    throw new IllegalStateException("desalinhado: " + new String(name, StandardCharsets.UTF_8)
                            + " em " + dataStart);
                }
            }
            System.out.println("alinhamento ok: " + apk.getName() + " (" + count + " entradas, "
                    + stored + " sem compressão)");
        }
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8;
    }

    private static int le32(byte[] b, int o) {
        return le16(b, o) | le16(b, o + 2) << 16;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        int n;
        while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
