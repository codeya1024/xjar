package io.xjar;

import io.xjar.key.XKey;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;

/**
 * JAR包加密器
 *
 * @author Payne 646742615@qq.com
 * 2018/11/22 15:27
 */
public class XJarEncryptor extends XWrappedEncryptor implements XEncryptor {
    private final int level;

    public XJarEncryptor(XEncryptor xEncryptor) {
        this(xEncryptor, Deflater.DEFAULT_COMPRESSION);
    }

    public XJarEncryptor(XEncryptor xEncryptor, int level) {
        super(xEncryptor);
        this.level = level;
    }

    @Override
    public void encrypt(XKey key, File src, File dest) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(src);
                FileOutputStream fos = new FileOutputStream(dest)
        ) {
            encrypt(key, fis, fos);
        }
    }

    @Override
    public void encrypt(XKey key, InputStream in, OutputStream out) throws IOException {
        JarArchiveInputStream zis = null;
        JarArchiveOutputStream zos = null;
        try {
            zis = new JarArchiveInputStream(in);
            zos = new JarArchiveOutputStream(out);
            zos.setLevel(level);
            NoCloseInputStream nis = new NoCloseInputStream(zis);
            NoCloseOutputStream nos = new NoCloseOutputStream(zos);
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                // 内嵌JAR包不压缩
                if (entry.getName().endsWith(".jar")) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    CheckedOutputStream cos = new CheckedOutputStream(bos, new CRC32());
                    encrypt(key, nis, cos);
                    JarArchiveEntry jarArchiveEntry = new JarArchiveEntry(entry.getName());
                    jarArchiveEntry.setMethod(JarArchiveEntry.STORED);
                    jarArchiveEntry.setSize(bos.size());
                    jarArchiveEntry.setCrc(cos.getChecksum().getValue());
                    zos.putArchiveEntry(jarArchiveEntry);
                    ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                    XKit.transfer(bis, nos);
                }
                // 其他资源做压缩
                else {
                    JarArchiveEntry jarArchiveEntry = new JarArchiveEntry(entry.getName());
                    zos.putArchiveEntry(jarArchiveEntry);
                    try (OutputStream eos = encrypt(key, nos)) {
                        XKit.transfer(zis, eos);
                    }
                }
                zos.closeArchiveEntry();
            }
            zos.finish();
        } finally {
            XKit.close(zis);
            XKit.close(zos);
        }
    }
}
