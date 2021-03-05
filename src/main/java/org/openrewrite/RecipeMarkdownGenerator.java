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
import java.util.*;

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
        Path recipesPath = outputPath.resolve("reference/recipes");
        if (!recipesPath.toFile().mkdirs()) {
            throw new RuntimeException("Unable to create directory " + recipesPath);
        }
        Environment env = Environment.builder().scanClasspath(emptyList()).build();
        List<RecipeDescriptor> recipeDescriptors = new ArrayList<>(env.listRecipeDescriptors());
        recipeDescriptors.sort(Comparator.comparing(RecipeDescriptor::getName));
        for (RecipeDescriptor recipeDescriptor : recipeDescriptors) {
            writeRecipe(recipeDescriptor, recipesPath);
        }
        Path summarySnippetPath = outputPath.resolve("SUMMARY_snippet.md");
        Path recipeIndexPath = recipesPath.resolve("README.md");
        BufferedWriter summarySnippetWriter;
        BufferedWriter recipeIndexWriter;
        try {
            summarySnippetWriter = Files.newBufferedWriter(summarySnippetPath, StandardOpenOption.CREATE);
            recipeIndexWriter = Files.newBufferedWriter(recipeIndexPath, StandardOpenOption.CREATE);
            summarySnippetWriter.write("* [Recipes](reference/recipes/README.md)");
            summarySnippetWriter.newLine();
            recipeIndexWriter.write("# Recipes");
            recipeIndexWriter.newLine();
            for (RecipeDescriptor recipe : recipeDescriptors) {
                summarySnippetWriter.write("  * [" + recipe.getDisplayName() + "](reference/recipes/" + recipe.getName().toLowerCase() + ".md)");
                summarySnippetWriter.newLine();
                recipeIndexWriter.write("* [" + recipe.getDisplayName() + "](" + recipe.getName().toLowerCase() + ".md)");
                recipeIndexWriter.newLine();
            }
            summarySnippetWriter.close();
            recipeIndexWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeRecipe(RecipeDescriptor recipeDescriptor, Path outputPath) {
        Path recipeMarkdownPath = outputPath.resolve(recipeDescriptor.getName().toLowerCase() + ".md");
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE);
            writer.write("# " + recipeDescriptor.getDisplayName());
            writer.newLine();
            writer.newLine();
            writer.write("**" + recipeDescriptor.getName() + "**  ");
            writer.newLine();
            writer.write("_"  + recipeDescriptor.getDescription() + "_");
            writer.newLine();
            writer.newLine();
            if (!recipeDescriptor.getTags().isEmpty()) {
                writer.write("### Tags");
                writer.newLine();
                writer.newLine();
                for (String tag : recipeDescriptor.getTags()) {
                    writer.write("* " + tag);
                    writer.newLine();
                }
                writer.newLine();
            }
            if (!recipeDescriptor.getOptions().isEmpty()) {
                writer.write("### Options");
                writer.newLine();
                writer.newLine();
                for (OptionDescriptor option : recipeDescriptor.getOptions()) {
                    StringBuilder optionBuilder = new StringBuilder("* ")
                            .append(option.getName())
                            .append(": ")
                            .append(option.getType());
                    if (option.isRequired()) {
                        optionBuilder.append('!');
                    }
                    writer.write(optionBuilder.toString());
                    writer.newLine();
                    writer.write("\t* " + option.getDescription());
                    writer.newLine();
                }
                writer.newLine();
            }
            if (!recipeDescriptor.getRecipeList().isEmpty()) {
                writer.write("### Recipe list");
                writer.newLine();
                writer.newLine();
                for (RecipeDescriptor recipe : recipeDescriptor.getRecipeList()) {
                    writer.write("* [" + recipe.getDisplayName() + "](" + recipe.getName() + ".md)");
                    writer.newLine();
                    if (!recipe.getOptions().isEmpty()) {
                        for (OptionDescriptor option : recipe.getOptions()) {
                            if (option.getValue() != null) {
                                writer.write("\t* " + option.getName() + ": " + option.getValue().toString());
                                writer.newLine();
                            }
                        }

                    }
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
