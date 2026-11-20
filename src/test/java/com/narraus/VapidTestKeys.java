package com.narraus;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.Base64;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Antaa testeille kertakäyttöisen VAPID-avainparin, joka generoidaan joka ajolla.
 * Näin testit eivät tarvitse oikeita avaimia eikä repositorioon tarvitse tallentaa yhtään yksityistä avainta.
 */
public class VapidTestKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String[] PAIR = generate();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "narraus.push.public-key=" + PAIR[0],
                "narraus.push.private-key=" + PAIR[1],
                "narraus.push.subject=https://example.test").applyTo(context);
    }

    private static String[] generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            ECPoint w = ((ECPublicKey) pair.getPublic()).getW();
            byte[] publicKey = new byte[65];
            publicKey[0] = 4;
            System.arraycopy(fixed(w.getAffineX(), 32), 0, publicKey, 1, 32);
            System.arraycopy(fixed(w.getAffineY(), 32), 0, publicKey, 33, 32);
            byte[] privateKey = fixed(((ECPrivateKey) pair.getPrivate()).getS(), 32);
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            return new String[] {base64.encodeToString(publicKey), base64.encodeToString(privateKey)};
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] fixed(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[length];
        int copy = Math.min(bytes.length, length);
        System.arraycopy(bytes, bytes.length - copy, out, length - copy, copy);
        return out;
    }
}
