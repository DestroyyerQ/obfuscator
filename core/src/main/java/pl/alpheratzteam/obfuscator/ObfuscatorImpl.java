package pl.alpheratzteam.obfuscator;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import pl.alpheratzteam.obfuscator.Obfuscator;
import pl.alpheratzteam.obfuscator.exception.ObfuscatorException;
import pl.alpheratzteam.obfuscator.transformer.Transformer;
import pl.alpheratzteam.obfuscator.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

/**
 * @author Unix
 * @since 21.03.2020
 */

public class ObfuscatorImpl implements Obfuscator
{
    private final Logger logger;
    private final Map<String, ClassNode> classMap;
    private final Map<String, byte[]> fileMap;
    private final Set<Transformer> transformerSet;

    ObfuscatorImpl() {
        this.logger = Logger.getLogger("ObfuscatorImpl");
        this.classMap = new HashMap<>();
        this.fileMap = new HashMap<>();
        this.transformerSet = new HashSet<>();
    }

    @Override
    public void onStart() {
        this.logger.info("Loading transformers...");

        //transformers
        //use something

        this.logger.info("Loaded transformers (" + this.transformerSet.size() + ")!");
        this.logger.info("Loading jar...");

        final File source = new File("obfuscator");

        if (!source.exists() && source.mkdirs()) {
            this.logger.info("Created file obfuscator!");
        }

        final File inputFile = new File(source.getAbsolutePath(), "toObf.jar");
        final File outputFile = new File(FileUtil.renameExistingFile(new File(inputFile.getAbsolutePath().replace(".jar", "-obfuscated.jar"))));

        this.loadJar(inputFile);

        this.logger.info("Loaded jar!");

        this.transformerSet.stream().filter(Objects::nonNull).forEach(transformer -> {
            final long currentTime = System.currentTimeMillis();
            final String name = transformer.getClass().getSimpleName();

            this.logger.info(String.format("Running %s transformer...", name));
            this.classMap.values()
                    .forEach(transformer::visit);
            this.classMap
                    .values()
                    .forEach(transformer::after);
            this.logger.info(String.format("Finished running %s transformer. [%dms]", name, (System.currentTimeMillis() - currentTime)));
            this.logger.info("---------------------------------------");
        });

        this.logger.info("Saving jar...");

        try {
            try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outputFile))) {
                this.saveJar(jarOutputStream);
                this.logger.info("Saved jar!");
            }
        } catch (IOException ex) {
            throw new ObfuscatorException(ex);
        }
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public Map<String, ClassNode> getClassMap() {
        return this.classMap;
    }

    @Override
    public Map<String, byte[]> getFileMap() {
        return this.fileMap;
    }

    private void loadJar(File file) {
        try (JarFile jarFile = new JarFile(file)) {
            final Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                final JarEntry jarEntry = entries.nextElement();

                try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                    final byte[] bytes = IOUtils.toByteArray(inputStream);

                    if (!jarEntry.getName().endsWith(".class")) {
                        this.fileMap.put(jarEntry.getName(), bytes);
                        continue;
                    }

                    final ClassNode classNode = new ClassNode();
                    final ClassReader classReader = new ClassReader(bytes);

                    classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                    this.classMap.put(classNode.name, classNode);
                }
            }
        } catch (IOException ex) {
            throw new ObfuscatorException(ex);
        }
    }

    private void saveJar(JarOutputStream jarOutputStream) throws IOException {
        for (ClassNode classNode : this.classMap.values()) {
            final JarEntry jarEntry = new JarEntry(classNode.name + ".class");
            final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

            jarOutputStream.putNextEntry(jarEntry);

            classNode.accept(classWriter);
            jarOutputStream.write(classWriter.toByteArray());
            jarOutputStream.closeEntry();
        }

        for (Map.Entry<String, byte[]> entry : this.fileMap.entrySet()) {
            jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
            jarOutputStream.write(entry.getValue());
            jarOutputStream.closeEntry();
        }
    }
}