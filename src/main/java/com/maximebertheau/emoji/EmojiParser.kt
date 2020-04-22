package com.maximebertheau.emoji

object EmojiParser {
    private val aliasMatchingRegex = Regex(""":([\w_+-]+)(?:(?:\||::)((type_|skin-tone-\d+)[\w_]*))*:""")
    private val aliasMatchingRegexOptionalColon = Regex(""":?([\w_+-]+)(?:(?:\||::)((type_|skin-tone-\d+)[\w_]*))*:?""")

    fun parseToAliases(input: String): String {
        val root = EmojiManager.EMOJI_TREE.root

        var node: EmojiTrie.Node? = null
        var path = mutableListOf<Char>()

        fun findFirstNode(c: Char): EmojiTrie.Node {
            path = mutableListOf()
            return if (root.hasChild(c)) {
                path.add(c)
                root.getChild(c)!!
            } else {
                root
            }
        }

        val matchedEmojis = mutableMapOf<String, Emoji>()
        for (c in input) {
            when {
                node == null -> {
                    node = findFirstNode(c)
                }
                node.hasChild(c) -> {
                    path.add(c)
                    node = node.getChild(c)
                }
                node.emoji != null -> {
                    matchedEmojis[path.joinToString(separator = "")] = node.emoji!!
                    node = findFirstNode(c)
                }
                node.emoji == null -> {
                    node = findFirstNode(c)
                }
            }
        }

        if (node?.emoji != null) {
            matchedEmojis[path.joinToString(separator = "")] = node.emoji!!
        }

        return matchedEmojis.entries.fold(input) { acc, (toReplace, emoji) ->
            acc.replace(Regex("$toReplace\\u200d?"), ":${emoji.aliases.first()}:")
        }
    }

    fun parseToUnicode(input: String): String {
        return input.getUnicodesForAliases().entries.fold(input) { acc, (alias, emoji) ->
            acc.replace(alias, emoji)
        }
    }

    private fun String.getUnicodesForAliases(): Map<String, String> {
        val input = this
        val results = aliasMatchingRegex.findAll(input)

        if (results.none()) return emptyMap()

        val uniqueMatches = mutableMapOf<String, String>()

        results.forEach { result ->
            val fullAlias = input.substring(result.range)

            if (uniqueMatches.containsKey(fullAlias)) return@forEach

            uniqueMatches[fullAlias] = getUnicodeFromAlias(fullAlias) ?: return@forEach
        }

        return uniqueMatches.toSortedMap(Comparator { o1, o2 ->
            o1.length - o2.length // Execute the longer first so emojis with skin variations are executed before the ones without
        })
    }

    private fun getUnicodeFromAlias(input: String): String? {
        val results = aliasMatchingRegexOptionalColon.findAll(input)

        if (results.none()) return null

        val match = results.first()

        val aliasMatch = match.groups.drop(1).firstOrNull() ?: return null
        val alias = input.substring(aliasMatch.range)

        val skinVariationsString = input.substring(aliasMatch.range.last)
                .split(':')
                .map { it.trimStart(':').trimEnd(':') }
                .filter { it.isNotEmpty() }

        val emoji = EmojiManager.getForAlias(alias).firstOrNull() ?: return null

        val skinVariations = skinVariationsString.mapNotNull {
            SkinVariationType.fromAlias(it)
        }

        return emoji.skinVariations.firstOrNull {
            it.types == skinVariations
        }?.unified?.unicode ?: emoji.unified.unicode
    }
}
