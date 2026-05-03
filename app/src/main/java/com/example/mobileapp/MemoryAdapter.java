package com.example.mobileapp;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder> {

    private List<Memory> memories;

    public MemoryAdapter(List<Memory> memories) {
        this.memories = memories;
    }

    public void setMemories(List<Memory> memories) {
        this.memories = memories;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new MemoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
        Memory memory = memories.get(position);
        holder.emotionText.setText(memory.getEmotion());
        holder.noteText.setText(memory.getNote());
        holder.authorText.setText(memory.getAuthor() != null ? "By " + memory.getAuthor() : "");

        if (memory.getImagePath() != null && !memory.getImagePath().isEmpty()) {
            File imgFile = new File(memory.getImagePath());
            if (imgFile.exists()) {
                holder.imageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
            } else {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    @Override
    public int getItemCount() {
        return memories != null ? memories.size() : 0;
    }

    static class MemoryViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView emotionText;
        TextView noteText;
        TextView authorText;

        public MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.item_image);
            emotionText = itemView.findViewById(R.id.item_emotion);
            noteText = itemView.findViewById(R.id.item_note);
            authorText = itemView.findViewById(R.id.item_author);
        }
    }
}
