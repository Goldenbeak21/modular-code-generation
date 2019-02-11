package me.nallen.modularcodegeneration.parsetree

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * A representation of a "program", essentially just a sequence of lines of formulae with some extra keywords for
 * control flow, and support for variables.
 */
data class Program(
        val lines: ArrayList<ProgramLine> = ArrayList(),

        val variables: ArrayList<VariableDeclaration> = ArrayList()
) {
    companion object Factory {
        // Method for creating from a String (used in JSON parsing)
        @JsonCreator @JvmStatic
        fun generate(input: String): Program = generateProgramFromString(input)
    }

    /**
     * Generate the string notation equivalent of this program
     */
    @JsonValue
    fun getString(): String {
        return this.generateString()
    }

    /**
     * Adds a variable to the program.
     * A variable needs to have a name, type, and locality (internal or external).
     * In addition, if there is a default value desired for the variable (i.e. an initial value) then this is provided.
     */
    private fun addVariable(item: String, type: VariableType, locality: Locality = Locality.INTERNAL, default: ParseTreeItem? = null): Program {
        // Check that a variable by the same name doesn't already exist
        if(!variables.any {it.name == item}) {
            // Add the variable
            variables.add(VariableDeclaration(item, type, locality, default))

            // If a default value was provided, we want to check it for new variables too
            if(default != null)
                checkParseTreeForNewVariable(default, HashMap(), HashMap())
        }

        // Return the program for chaining
        return this
    }

    /**
     * Traverses the entire program to find all variables contained within it. Any external variables to the program are
     * provided through the "existing" argument.
     * In addition, it is recommended to provide the return type of any functions so that variable types can be
     * accurately captured
     */
    fun collectVariables(existing: List<VariableDeclaration> = ArrayList(), knownFunctionTypes: Map<String, VariableType?> = LinkedHashMap()): Map<String, VariableType> {
        val knownVariables = LinkedHashMap<String, VariableType>()

        // First, we need to record that we know all the external variables
        // This has to be done first so that we don't mistakenly add them as internal variables while parsing
        for(item in existing) {
            knownVariables[item.name] = item.type
        }

        // Now we can add each of the external variables to the program
        for(item in existing) {
            addVariable(item.name, item.type, Locality.EXTERNAL_INPUT, item.defaultValue)
        }

        // We now need to go through each line of the program to find variables, with one key point
        // We need to parse everything at *this level* before delving into branches (i.e. a breadth-first-search) so
        // that we correctly work out at what level the variable should be created at.
        // To achieve this, we keep track of any sub-programs that we need to parse, and do them later
        for(line in lines) {
            // For each line, we need to search any logic it may contain for any new variables
            when(line) {
                is Statement -> checkParseTreeForNewVariable(line.logic, knownVariables, knownFunctionTypes)
                is Assignment -> {
                    // When we come across a variable assignment, we need to see if we know it, and if not then add it.
                    // The type needs to be guessed from the logic that is assigned to it
                    if(!knownVariables.containsKey(line.variableName.name))
                        knownVariables[line.variableName.name] = line.variableValue.getOperationResultType(knownVariables, knownFunctionTypes)

                    // The following line will actually add the variable to the Program
                    checkParseTreeForNewVariable(line.variableName, knownVariables, knownFunctionTypes)
                    checkParseTreeForNewVariable(line.variableValue, knownVariables, knownFunctionTypes)
                }
                is Return -> checkParseTreeForNewVariable(line.logic, knownVariables, knownFunctionTypes)
                is IfStatement -> {
                    checkParseTreeForNewVariable(line.condition, knownVariables, knownFunctionTypes)
                    knownVariables.putAll(line.body.collectVariables(variables, knownFunctionTypes))
                }
                is ElseIfStatement -> {
                    checkParseTreeForNewVariable(line.condition, knownVariables, knownFunctionTypes)
                    knownVariables.putAll(line.body.collectVariables(variables, knownFunctionTypes))
                }
                is ElseStatement -> knownVariables.putAll(line.body.collectVariables(variables, knownFunctionTypes))
            }
        }

        // Return the list of variables found
        return knownVariables
    }

    /**
     * Finds and returns what type this Program will return.
     * If this Program uses any custom functions the return values of them must be explicitly given, so that return
     * types can be generated.
     * This will be null if there is no type returned from this Program
     */
    fun getReturnType(knownFunctionTypes: Map<String, VariableType?> = LinkedHashMap()): VariableType? {
        val bodiesToParse = ArrayList<Program>()

        val variableTypeMap = LinkedHashMap<String, VariableType>()
        for(variable in variables) {
            variableTypeMap[variable.name] = variable.type
        }

        // We start with no return type, until we find one. Whenever we find one we "combine" the return types to test
        // if they are the same type or not
        var currentReturnType: VariableType? = null

        for(line in lines) {
            // The only line types that matter in this case are Return statements, where we want to check what type it
            // returns, and branching instructions that contain sub-programs - which we also want to check.
            when(line) {
                is Return -> currentReturnType = combineReturnTypes(currentReturnType, line.logic.getOperationResultType(variableTypeMap, knownFunctionTypes))
                is IfStatement -> {
                    bodiesToParse.add(line.body)
                }
                is ElseIfStatement -> {
                    bodiesToParse.add(line.body)
                }
                is ElseStatement -> bodiesToParse.add(line.body)
            }
        }

        // Any sub-programs we found now need to be parsed too
        for(body in bodiesToParse) {
            currentReturnType = combineReturnTypes(currentReturnType, body.getReturnType(knownFunctionTypes))
        }

        // Return the final type we found
        return currentReturnType
    }

    /**
     * Combines two VariableTypes into a single VariableType that matches both of the arguments.
     * A null as input is treated as "don't care", and if the two arguments are both non-null and non-equal then no
     * common type could be found and an exception wil be thrown.
     */
    private fun combineReturnTypes(a: VariableType?, b: VariableType?): VariableType? {
        // If a is null, we don't care, just use b
        if(a == null)
            return b

        // If b is null, we don't care, just use a
        if(b == null)
            return a

        // Otherwise both non-null, check they're equal
        if(a != b)
            // Non-equal is an error
            throw IllegalArgumentException("Error in return types!")

        // They're both equal, so doesn't matter which we return
        return a
    }

    /**
     * Checks a Parse Tree for any new variables inside the program
     */
    private fun checkParseTreeForNewVariable(item: ParseTreeItem, variables: Map<String, VariableType>, functions: Map<String, VariableType?>) {
        // If we're currently at a Variable, we want to try add it!
        if(item is Variable) {
            addVariable(item.name, item.getOperationResultType(variables, functions))
        }

        // Recursively call for all children
        for(child in item.getChildren()) {
            checkParseTreeForNewVariable(child, variables, functions)
        }
    }
}

/**
 * The declaration of a Variable - including its name, type, locality and maybe a default value
 */
data class VariableDeclaration(
        var name: String,
        var type: VariableType,
        var locality: Locality,
        var defaultValue: ParseTreeItem? = null
)

/**
 * The locality of a Variable, can either be an Internal variable or an External variable
 */
enum class Locality {
    INTERNAL, EXTERNAL_INPUT
}

/**
 * A class that represents a single line of a Program.
 * This is how an object based representation of the Program is created, each Line can be one of a finite set of
 * operations that could be done.
 */
sealed class ProgramLine(var type: String)

// All of the line types
data class Statement(var logic: ParseTreeItem): ProgramLine("statement")
data class Assignment(var variableName: Variable, var variableValue: ParseTreeItem): ProgramLine("assignment")
data class Return(var logic: ParseTreeItem): ProgramLine("return")
data class IfStatement(var condition: ParseTreeItem, var body: Program): ProgramLine("ifStatement")
data class ElseStatement(var body: Program): ProgramLine("elseStatement")
data class ElseIfStatement(var condition: ParseTreeItem, var body: Program): ProgramLine("elseIfStatement")

/**
 * Generates a complete Program from a given input string
 */
fun generateProgramFromString(input: String): Program {
    val program = Program()

    // Each line of the Program should be the same as a line in the string, so we can split on newlines
    val lines = input.lines()

    // And now iterate over all the lines
    var skip = 0
    var i = 0
    for(line in lines) {
        i++

        // If we've just parsed a line that included a body, then we'll use skip to skip the next lines
        if(skip > 0) {
            skip--
            continue
        }

        // Only need to look at non-blank lines
        if(line.isNotBlank()) {
            var programLine: ProgramLine? = null

            // A regex that finds conditionals, either "if", "elseif", "else if", or "else"
            val conditionalRegex = Regex("^\\s*((if|else(\\s*)if)\\s*\\((.*)\\)|else)\\s*\\{\\s*\$")
            // A regex that finds return statements
            val returnRegex = Regex("^\\s*return\\s+(.*)\\s*$")
            // A regex that finds assignments
            val assignmentRegex = Regex("^\\s*([-_a-zA-Z0-9]+)\\s*=\\s*(.*)\\s*$")

            // Check if the current line is a conditional
            val match = conditionalRegex.matchEntire(line)
            if(match != null) {
                // Yes it's a conditional!
                // Now we want to get the inner body of the conditional by looking for the matching close bracket
                val bodyText = getTextUntilNextMatchingCloseBracket(lines.slice(IntRange(i, lines.size-1)).joinToString("\n"))

                // We now need to know to skip the same number of lines as we just fetched
                skip = bodyText.count {it == '\n'} +1

                // And create the Program for the inner body text that we'll now use
                val body = generateProgramFromString(bodyText)

                // Now we need to figure out which exact conditional it was
                if(match.groupValues[1] == "else") {
                    // Else Statement
                    programLine = ElseStatement(body)
                }
                else {
                    // Either If or ElseIf, meaning that it has a condition too
                    val condition = ParseTreeItem.generate(match.groupValues[4])
                    if(match.groupValues[2] == "if")
                        // If Statement
                        programLine = IfStatement(condition, body)
                    else if(match.groupValues[2].startsWith("else"))
                        // ElseIf Statement
                        programLine = ElseIfStatement(condition, body)
                }
            }
            else {
                // Not a conditional, check if it's a return statement
                val returnMatch = returnRegex.matchEntire(line)
                programLine = if(returnMatch != null) {
                    // Yes it's a return, create the Return Line
                    Return(ParseTreeItem.generate(returnMatch.groupValues[1]))
                } else {
                    // Not a return statement either, check if it's an assignment
                    val assignmentMatch = assignmentRegex.matchEntire(line)
                    if(assignmentMatch != null) {
                        // Yes it's an assignment, create the Assignment Line
                        Assignment(Variable(assignmentMatch.groupValues[1]), ParseTreeItem.generate(assignmentMatch.groupValues[2]))
                    } else {
                        // Not an assignment either, so must just be a Statement Line
                        Statement(ParseTreeItem.generate(line))
                    }
                }
            }

            // If we managed to find a line we want to add it
            if(programLine != null)
                program.lines.add(programLine)
        }
    }

    // Return the program for chaining
    return program
}

/**
 * Searches the string for the next matching close bracket, and returns it
 */
fun getTextUntilNextMatchingCloseBracket(input: String): String {
    // We start off wanting to find one close bracket
    var bracketsToFind = 1
    // Iterate through the string
    for(i in 0 until input.length) {
        // Finding an open bracket increments the counter
        if(input[i] == '{')
            bracketsToFind++
        // Finding a close bracket decrements the counter
        else if(input[i] == '}')
            bracketsToFind--

        // If the counter reaches 0 then we've found our matching close bracket!
        if(bracketsToFind == 0)
            // Return a substring until this point
            return input.substring(0, i)
    }

    // Uh oh, this means no matching close bracket was found
    throw IllegalArgumentException("Invalid program!")
}

/**
 * Generates a string version of the Program that can be output. Any formulae will be output in infix notation.
 *
 * This will recursively go through the Program whenever a condition with a sub-Program is found
 */
fun Program.generateString(): String {
    val builder = StringBuilder()

    // Iterate through every line
    lines
            .map {
                // Depending on the type, the generated output will be slightly different
                when(it) {
                    is Statement -> it.logic.generateString()
                    is Assignment -> "${it.variableName.generateString()} = ${it.variableValue.generateString()}"
                    is Return -> "return ${it.logic.generateString()}"
                    // The other ones (conditionals) all require indenting for their bodies too
                    is IfStatement -> "if(${it.condition.generateString()}) {\n${it.body.generateString().prependIndent("  ")}\n}"
                    is ElseIfStatement -> "else if(${it.condition.generateString()}) {\n${it.body.generateString().prependIndent("  ")}\n}"
                    is ElseStatement -> "else {\n${it.body.generateString().prependIndent("  ")}\n}"
                }
            }
            // And append them all together!
            .forEach { builder.appendln(it) }

    // And return the generated string
    return builder.toString().trimEnd()
}

/**
 * Sets the value of a variable in the Program
 *
 * This sets the value of any variable in this Program with name "key" to "value", no matter how many operations
 * deep
 */
fun Program.setParameterValue(key: String, value: ParseTreeItem): Program {
    // We need to go through any existing default values for variables to see if it exists in there
    for(variable in variables) {
        variable.defaultValue?.setParameterValue(key, value)
    }

    // And then go through each line
    for(line in lines) {
        // For each line we need to call the method that sets the parameter value for any ParseTreeItem we find
        // We also have to recursively call this method for any bodies we find under any conditionals
        when(line) {
            is Statement -> line.logic.setParameterValue(key, value)
            is Assignment -> {
                line.variableName.setParameterValue(key, value)
                line.variableValue.setParameterValue(key, value)
            }
            is Return -> line.logic.setParameterValue(key, value)
            is IfStatement -> {
                line.condition.setParameterValue(key, value)
                line.body.setParameterValue(key, value)
            }
            is ElseIfStatement -> {
                line.condition.setParameterValue(key, value)
                line.body.setParameterValue(key, value)
            }
            is ElseStatement -> line.body.setParameterValue(key, value)
        }
    }

    // Return the program for chaining
    return this
}