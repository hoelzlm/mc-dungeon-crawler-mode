package dev.crawlmode.engine.floorgen;

/** A solid block of a template: a block id and whether it is a secret (unprotected) wall block. */
public record TemplateBlock(String blockId, boolean secret) {}
