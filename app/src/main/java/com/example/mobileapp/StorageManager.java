package com.example.mobileapp;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StorageManager {
    private static final String FILE_NAME = "memories.json";

    public static List<Memory> loadMemories(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<Memory>>(){}.getType();
            List<Memory> memories = gson.fromJson(reader, listType);
            if (memories == null) return new ArrayList<>();
            return memories;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveMemories(Context context, List<Memory> memories) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(memories, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void addMemory(Context context, Memory memory) {
        List<Memory> memories = loadMemories(context);
        memories.add(memory);
        saveMemories(context, memories);
    }
}
