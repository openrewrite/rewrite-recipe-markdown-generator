package org.openrewrite;

import org.openrewrite.config.Environment;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

import static java.util.Collections.emptyList;

@Command(name = "rewrite-recipe-markdown-generator", mixinStandardHelpOptions = true,
        description = "Generates documentation for OpenRewrite recipes in markdown format",
        version = "1.0.0-SNAPSHOT")
class RecipeMarkdownGenerator implements Runnable {

    @Parameters(index = "0", description = "Destination directory for generated recipe markdown")
    private String destinationDirectoryName;

    @Override
    public void run() {
        Path outputPath = Paths.get(destinationDirectoryName);
        Environment env = Environment.builder().scanClasspath(emptyList()).build();
        Collection<RecipeDescriptor> recipeDescriptors = env.listRecipeDescriptors();
        for (RecipeDescriptor recipeDescriptor : recipeDescriptors) {
            writeRecipe(recipeDescriptor, outputPath);
        }
        Path indexPath = outputPath.resolve("index.md");
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(indexPath, StandardOpenOption.CREATE_NEW);
            writer.write("# Recipes");
            writer.newLine();
            for (RecipeDescriptor recipe : recipeDescriptors) {
                writer.write("- [" + recipe.getDisplayName() + "](" + recipe.getName() + ".md)");
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeRecipe(RecipeDescriptor recipeDescriptor, Path outputPath) {
        Path recipeMarkdownPath = outputPath.resolve(recipeDescriptor.getName() + ".md");
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE_NEW);
            writer.write("# " + recipeDescriptor.getDisplayName());
            writer.newLine();
            writer.newLine();
            writer.write("---");
            writer.newLine();
            writer.write("**" + recipeDescriptor.getName() + "**  ");
            writer.newLine();
            writer.write("*"  + recipeDescriptor.getDescription() + "*");
            writer.newLine();
            if (!recipeDescriptor.getTags().isEmpty()) {
                writer.write("## Tags");
                writer.newLine();
                for (String tag : recipeDescriptor.getTags()) {
                    writer.write("- " + tag);
                    writer.newLine();
                }
            }
            if (!recipeDescriptor.getOptions().isEmpty()) {
                writer.write("## Options");
                writer.newLine();
                for (OptionDescriptor option : recipeDescriptor.getOptions()) {
                    StringBuilder optionBuilder = new StringBuilder("- ")
                            .append(option.getName())
                            .append(": ")
                            .append(option.getType());
                    if (option.isRequired()) {
                        optionBuilder.append('!');
                    }
                    writer.write(optionBuilder.toString());
                    writer.newLine();
                    writer.write("\t- " + option.getDescription() + "*");
                    writer.newLine();
                }
            }

            if (!recipeDescriptor.getRecipeList().isEmpty()) {
                writer.write("## Dependencies");
                writer.newLine();
                for (RecipeDescriptor recipe : recipeDescriptor.getRecipeList()) {
                    writer.write("- [" + recipe.getDisplayName() + "](" + recipe.getName() + ".md)");
                    writer.newLine();
                }
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new RecipeMarkdownGenerator()).execute(args);
        System.exit(exitCode);
    }
}
