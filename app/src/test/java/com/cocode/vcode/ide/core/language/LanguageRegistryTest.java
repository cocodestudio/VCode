package com.cocode.vcode.ide.core.language;

import com.cocode.vcode.ide.core.language.base.LanguageDefinition;
import com.cocode.vcode.ide.core.language.base.LanguageRegistry;
import com.cocode.vcode.ide.core.model.FileType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LanguageRegistryTest {

    @Test
    public void testBuiltInLanguageDefinitions() {
        LanguageRegistry registry = LanguageRegistry.getInstance();

        LanguageDefinition jsDef = registry.getDefinition("javascript");
        assertNotNull(jsDef);
        assertEquals("JavaScript", jsDef.getDisplayName());
        assertEquals("//", jsDef.getLineCommentPrefix());

        LanguageDefinition htmlDef = registry.getDefinitionForExtension("html");
        assertNotNull(htmlDef);
        assertEquals("HTML", htmlDef.getDisplayName());
        assertEquals("<!--", htmlDef.getBlockCommentStart());
    }

    @Test
    public void testCustomLanguageDefinitionRegistration() {
        LanguageRegistry registry = LanguageRegistry.getInstance();

        LanguageDefinition pythonDef = new LanguageDefinition.Builder("python")
                .displayName("Python")
                .addExtension("py")
                .lineComment("#")
                .fallbackFileType(FileType.TEXT)
                .build();

        registry.registerDefinition(pythonDef);

        LanguageDefinition retrieved = registry.getDefinition("python");
        assertNotNull(retrieved);
        assertEquals("Python", retrieved.getDisplayName());
        assertEquals("#", retrieved.getLineCommentPrefix());

        LanguageDefinition byExt = registry.getDefinitionForExtension("py");
        assertNotNull(byExt);
        assertEquals("Python", byExt.getDisplayName());

        // Test FileType fallback resolution
        FileType resolvedType = FileType.fromExtension("py");
        assertEquals(FileType.TEXT, resolvedType);
    }
}
