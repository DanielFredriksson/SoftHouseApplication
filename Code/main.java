/**
 * Constructed by Daniel Fredriksson on assignment by Softhouse.
 * Doesn't get any more interesting than that!
 *  
 * 
 * 2020-11-04 --> 2020-01-06
 */
import java.io.File;        // File-reading
import java.util.Scanner;   // Input 
import java.io.FileWriter;  // Output
import java.io.IOException;
import java.util.Queue;         // Converted data containers
import java.util.LinkedList;    // 
import java.util.Stack;         // 
import java.util.HashMap;           // Type containers
import java.util.Map;               //

class Application {
    private static String exampleSource = "./example.txt";
    private static String megaExampleSource = "./megaExample.txt";
    private static String exampleDestination = "./example_converted.txt";
    public static int SLEEPTIME = 1;

    static String checkSourceAndDestination(String[] args, String sourcePath, String destinationPath) {
        // If input was received assume that it was the source path.
        if (args.length > 0) {
            sourcePath = args[0];
        }

        // Check for correct sourcepath
        Scanner userInput = new Scanner(System.in);
        boolean sourceCorrect = false;
        String currentInput = "";
        while (!sourceCorrect) {
            print("OldFormattedFilePath: \"" + sourcePath + "\", is that correct? (1/x)");
            currentInput = userInput.nextLine();
            if (currentInput.equals("1")) {
                sourceCorrect = true;
            } else {
                print("Then please input path to correct source:");
                sourcePath = userInput.nextLine();
            }
        }

        // Check if destination already exists
        File destinationFile = new File(destinationPath);
        if (destinationFile.exists()) {
            print("Converted file: " + destinationPath + " already exists, overwrite? (1/x)");
            if (!userInput.nextLine().equals("1")) {
                print("Not overwriting the file, exiting program...");
                userInput.close();
                return null;
            }
        }
        userInput.close();
        return sourcePath;
    }

    static Map<Character, Type> addTypes() {
        Map<Character, Type> types = new HashMap<>();
        types.put('P', new Type(
            'P',                                                // What's the char-id used by the program?
            new String[]{"person", "firstname", "lastname"},    // What should element strings be converted to?
            new char[]{'T', 'A', 'F'}                           // What are the allowed children for this type?
        ));
        types.put('A', new Type(
            'A', 
            new String[]{"adress", "street", "city", "zipcode"}, 
            null
        ));
        types.put('T', new Type(
            'T', 
            new String[]{"phone", "mobile", "landline"}, 
            null
        ));
        types.put('F', new Type(
            'F', 
            new String[]{"family", "name", "born"}, 
            new char[]{'T', 'A'} 
        ));
        return types;
    }

    static int getLinesInFile(String sourcePath) {
        int linesInFile = 0;
        // Determine size of file to minimize unnecessary container expansions.
        try {
            File fileObject = new File(sourcePath);
            Scanner fileReader = new Scanner(fileObject);
            while (fileReader.hasNextLine()) {
                fileReader.nextLine();
                linesInFile++;
            }
            fileReader.close();
        } catch (Exception e) {
            print("Failed to read file");
            e.printStackTrace();
        }

        return linesInFile;
    }

    static void print(String string) {
        System.out.println(string);
    }

    public static void main(String[] args) {
        print("Running Old-To-XML converter...");
        String destinationPath = exampleDestination;

        // Some checks to make life easier for the user.
        String sourcePath = checkSourceAndDestination(args, exampleSource, destinationPath);
        if (sourcePath == null) {
            return;
        }

        // Insert the different types of formats and how they should be treated.
        Map<Character, Type> types = addTypes();

        // Construct a block cycler which input and output threads will work with.
        // Can read size of source via 'getLinesInFile' and adapt blocks accordingly
        BlockManager sharedBlockManager = new BlockManager(10, 20);
        InputThread readerThread = new InputThread();
        OutputThread writerThread = new OutputThread();

        // -+-+-+-+-+-+-+-+- Start reader-thread -+-+-+-+-+-+-+-+- 
        // OBS: Currently no check for when the reader catches up to the writer,
        // this becomes a problem when other things cause the writer-thread
        // to become slow or the blockmanager is too small
        readerThread.main(sharedBlockManager, sourcePath, types);

        // -+-+-+-+-+-+-+-+- Start writer-thread -+-+-+-+-+-+-+-+- 
        writerThread.main(sharedBlockManager, destinationPath);

        // Wait for both to become ready.
        while (!sharedBlockManager.readerDone || !sharedBlockManager.writerDone) {
            try { Thread.sleep(1000); } catch ( InterruptedException e) { e.printStackTrace(); }
        }
        
        print("Conversion successful! ConvertedFilePath: " + destinationPath);
        // print("Press any key to exit.");
        // Scanner tempInput = new Scanner(System.in);
        // while (tempInput.hasNextLine()) {
        //     String temp = tempInput.nextLine();
        // }
        // tempInput.close();
    }
}

class Type {
    char id = '0';
    String parent = "";
    String[] newFormat = null;
    char[] allowedChildren = null;
    public Type(char id, String[] newFormat, char[] allowedChildren) {
        this.id = id;
        this.newFormat = newFormat;
        this.allowedChildren = allowedChildren;
    }
}

class Block {
    Queue<String> data = null;
    boolean open = true;
    public Block(int blockCapacity) {
        this.data = new LinkedList<String>();
    }
}

class BlockManager {
    Block[] blocks = null;
    int readerAt = 0;
    int writerAt = 0;
    int blockCount = -1;
    int blockCapacity = -1;
    boolean readerDone = false;
    boolean writerDone = false;

    public BlockManager(int blockCount, int blockCapacity) {
        this.blockCount = blockCount;
        this.blockCapacity = blockCapacity;
        this.blocks = new Block[this.blockCount];
        for(int i = 0; i < this.blockCount; i++) {
            this.blocks[i] = new Block(this.blockCapacity);
        }
    }

    void addData(String line) {
        // Switch block if necessary
        if (this.blocks[readerAt].data.size() == this.blockCapacity) {
            Application.print("Reader completely filled block: " + this.readerAt);
            this.readerAt = (this.readerAt + 1) % this.blockCount;
        }

        this.blocks[readerAt].data.add(line);
    }
    
    boolean timeToWrite() {
        if (blocks[writerAt].data.size() == this.blockCapacity)
            return true;
        else if (readerDone) {
            return true;
        }
        return false;
    }

    void writeData(FileWriter writer) {
        // Sleep while reader and writer are at the same block and reader is not done.
        while (writerAt == readerAt && !readerDone) {
            Application.print("Writer waiting for reader to finish at: " + writerAt);
            try { Thread.sleep(Application.SLEEPTIME); } catch ( InterruptedException e) { e.printStackTrace(); }
        }

        Queue<String> data = this.blocks[writerAt].data;
        try {
            if (data.size() == 0 && readerDone) {
                writerDone = true;
                Application.print("Writer is finished!");
                return;
            } else {
                while (data.size() > 0) {
                    writer.write(data.poll());
                }
                Application.print("Writer done with block: " + writerAt);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.writerAt = (this.writerAt + 1) % this.blockCount;
    }
}

class InputThread implements Runnable {
    BlockManager blockManager = null;
    String sourcePath = "";
    Map<Character, Type> types = null;

    public void main(BlockManager shared, String sourcePath, Map<Character, Type> types) {
        this.blockManager = shared;
        this.sourcePath = sourcePath;
        this.types = types;
        Thread myThread = new Thread(this);
        myThread.start();
    }

    static boolean isFormattedCorrectly(Type type, String[] splitLine) {
        if (type.newFormat.length!= splitLine.length)
            return false;
        return true;
    }

    public void run() {
        try {
            Thread.sleep(Application.SLEEPTIME);
            int linesParsed = 0;
            int currentDepth = 1;
            Stack<String> closeTags = new Stack<String>();
            Stack<Character> dadStack = new Stack<Character>();
            Application.print("Reading old formatted file...");
            try {
                // Access File
                File fileObject = new File(sourcePath);
                Scanner fileReader = new Scanner(fileObject);   // Intellisense error, closes right before 'catch'
                blockManager.addData("<people>\n");
                closeTags.add("</people>");

                // Parse file per row/line
                while (fileReader.hasNextLine()) {
                    try { Thread.sleep(Application.SLEEPTIME); } catch ( InterruptedException e) { e.printStackTrace(); }
                    String currentLine = fileReader.nextLine();
                    String[] splitData = currentLine.split("\\|", 4);
                    Type currentType = types.get(splitData[0].charAt(0));

                    // Check if the line is formatted correctly
                    if (!isFormattedCorrectly(currentType, splitData)) {
                        String errorMsg = sourcePath + ": Row #" + linesParsed + " seems to be corrupted: " + currentLine;
                        throw new Exception(errorMsg);
                    }
                    
                    // If two parents meet, remove the influence of the last parent.
                    if (dadStack.size() > 0) {
                        if (dadStack.contains(currentType.id)) {
                            while (dadStack.pop() != currentType.id) {
                                currentDepth--;
                                blockManager.addData(closeTags.pop() + "\n");
                            }
                            currentDepth--;
                            blockManager.addData(closeTags.pop() + "\n");
                        }
                    }

                    // Is being re-made alot, use one and modify it instead.
                    String depthStr = "";
                    for(int i = 0; i < currentDepth; i++) {
                        depthStr += "\t";
                    }

                    blockManager.addData(depthStr + "<" + currentType.newFormat[0] + ">\n");
                    for(int i = 1; i < currentType.newFormat.length; i++) {
                        // Add open tag
                        String open = depthStr + "\t" + "<" + currentType.newFormat[i] + ">";
                        // Add data
                        String data = splitData[i];
                        // Add close tag
                        String close = "</" + currentType.newFormat[i] + ">";
                        blockManager.addData(open + data + close + "\n");
                    }

                    closeTags.push(depthStr + "</" + currentType.newFormat[0] + ">");
                    
                    // Currently assuming that all parents which can have children,
                    // has atleast one child.
                    // If we're a parent, increase depth and save
                    if (currentType.allowedChildren != null) {
                        // Increase the depth
                        currentDepth++;
                        // Save dadID
                        dadStack.add(currentType.id);
                    } else {
                        blockManager.addData(closeTags.pop() + "\n");
                    }

                    linesParsed++;
                }

                while(closeTags.size() > 0) {
                    blockManager.addData(closeTags.pop() + "\n");
                }

                fileReader.close();
                this.blockManager.readerDone = true;
                Application.print("Reader is finished!");

            } catch (Exception e) {
                Application.print("Failed to read file");
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
} 

class OutputThread implements Runnable {
    BlockManager blockManager = null;
    String destinationPath = "";
    public void main(BlockManager shared, String destinationPath) {
        this.blockManager = shared;
        this.destinationPath = destinationPath;
        Thread myThread = new Thread(this);
        myThread.start();
    }
    public void run() {
        try {
            File outputFile = new File(destinationPath);
            FileWriter writer = new FileWriter(destinationPath);

            if (outputFile.exists() || outputFile.createNewFile()) {
                while (!blockManager.writerDone) {
                    try { Thread.sleep(Application.SLEEPTIME); } catch ( InterruptedException e) { e.printStackTrace(); }

                    if (blockManager.timeToWrite()) {
                        blockManager.writeData(writer);
                    }
                }
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}