package com.example.mobileapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sidebar adapter for the friends filter list on the map.
 * Each row shows a friend's avatar initial, name and a checkbox.
 * Selected friends have their memories shown on the map.
 */
public class SidebarFriendsAdapter extends RecyclerView.Adapter<SidebarFriendsAdapter.VH> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> selectedFriends);
    }

    private final List<String> friends;
    private final Set<String> selected;
    private final OnSelectionChangedListener listener;

    public SidebarFriendsAdapter(List<String> friends,
                                  Set<String> initiallySelected,
                                  OnSelectionChangedListener listener) {
        this.friends = friends;
        this.selected = new HashSet<>(initiallySelected);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_friend, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String name = friends.get(position);

        // Avatar initial
        h.tvAvatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());

        // Name label
        h.tvName.setText(name);

        // Sync checkbox without firing listener
        h.cbFriend.setOnCheckedChangeListener(null);
        h.cbFriend.setChecked(selected.contains(name));

        // Row click or checkbox click
        h.itemView.setOnClickListener(v -> {
            boolean nowChecked = !h.cbFriend.isChecked();
            h.cbFriend.setChecked(nowChecked);
            if (nowChecked) selected.add(name);
            else selected.remove(name);
            listener.onSelectionChanged(new HashSet<>(selected));
        });

        h.cbFriend.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) selected.add(name);
            else selected.remove(name);
            listener.onSelectionChanged(new HashSet<>(selected));
        });
    }

    @Override
    public int getItemCount() { return friends.size(); }

    /** Returns a copy of the currently selected set. */
    public Set<String> getSelected() { return new HashSet<>(selected); }

    /** Select all friends. */
    public void selectAll() {
        selected.addAll(friends);
        notifyDataSetChanged();
        listener.onSelectionChanged(new HashSet<>(selected));
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName;
        CheckBox cbFriend;

        VH(@NonNull View itemView) {
            super(itemView);
            tvAvatar  = itemView.findViewById(R.id.tv_avatar);
            tvName    = itemView.findViewById(R.id.tv_friend_name);
            cbFriend  = itemView.findViewById(R.id.cb_friend);
        }
    }
}
