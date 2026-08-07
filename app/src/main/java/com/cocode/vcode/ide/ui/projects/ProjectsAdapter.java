package com.cocode.vcode.ide.ui.projects;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.Project;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.databinding.ItemProjectCardBinding;
import com.cocode.vcode.ide.utils.DateUtils;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ProjectsAdapter manages the display of the project list in a grid/list format.
 * It features custom swipe physics for quick actions (rename/delete) and dynamic
 * badge generation based on the file types present in each project.
 */
public class ProjectsAdapter extends RecyclerView.Adapter<ProjectsAdapter.ProjectViewHolder> {

    private final ProjectClickListener listener;
    private List<Project> projects = new ArrayList<>();

    /**
     * Tracks the view that is currently swiped open to ensure only one item
     * can be in the active swipe state at a time.
     */
    private View currentlySwipedView = null;

    /**
     * Initializes the adapter with a click listener for handling user interactions.
     *
     * @param listener Callback for project-related actions.
     */
    public ProjectsAdapter(ProjectClickListener listener) {
        this.listener = listener;
    }

    /**
     * Updates the underlying project list and calculates the minimal set of changes
     * using DiffUtil for efficient UI updates.
     *
     * @param newProjects The new list of projects to display.
     */
    public void setProjects(List<Project> newProjects) {
        List<Project> updatedList = newProjects != null ? newProjects : new ArrayList<>();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ProjectDiffCallback(this.projects, updatedList));
        this.projects = new ArrayList<>(updatedList);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Forces a specific project card to redraw its UI.
     * Useful for optimistic UI updates where a change is reflected immediately
     * before the full dataset is reloaded.
     *
     * @param projectId The unique ID of the project to update.
     */
    public void forceItemUpdate(String projectId) {
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getId().equals(projectId)) {
                notifyItemChanged(i); // Triggers a smooth native cross-fade animation
                break;
            }
        }
    }

    /**
     * Resets any currently swiped-open menu to its closed position.
     * Typically called when the parent RecyclerView starts scrolling.
     */
    public void closeSwipedItem() {
        if (currentlySwipedView != null) {
            currentlySwipedView.animate().translationX(0).setDuration(200).start();
            currentlySwipedView = null;
        }
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProjectCardBinding binding = ItemProjectCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ProjectViewHolder(binding, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        holder.bind(projects.get(position));
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    /**
     * Interface for handling interactions with project cards.
     */
    public interface ProjectClickListener {
        /**
         * Called when a project card is tapped.
         */
        void onProjectClick(Project project);

        /**
         * Called when the rename action is triggered from the swipe menu.
         */
        void onProjectRenameClick(Project project);

        /**
         * Called when the delete action is triggered from the swipe menu.
         */
        void onProjectDeleteClick(Project project);
    }

    /**
     * Implementation of DiffUtil.Callback to optimize RecyclerView updates.
     */
    private static class ProjectDiffCallback extends DiffUtil.Callback {
        private final List<Project> oldList;
        private final List<Project> newList;

        public ProjectDiffCallback(List<Project> oldList, List<Project> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Project oldP = oldList.get(oldItemPosition);
            Project newP = newList.get(newItemPosition);
            return oldP.getName().equals(newP.getName()) &&
                    oldP.getLastModifiedAt() == newP.getLastModifiedAt() &&
                    oldP.getFileCount() == newP.getFileCount();
        }
    }

    /**
     * ViewHolder class that handles the lifecycle and logic of a single project card item.
     */
    public static class ProjectViewHolder extends RecyclerView.ViewHolder {

        private final ItemProjectCardBinding binding;
        private final ProjectsAdapter adapter;
        private String currentBoundProjectId = "";

        public ProjectViewHolder(@NonNull ItemProjectCardBinding binding, ProjectsAdapter adapter) {
            super(binding.getRoot());
            this.binding = binding;
            this.adapter = adapter;

            setupCircularActionButtons(itemView.getContext());
            setupTypefaces(itemView.getContext());
            setupListeners();
        }

        /**
         * Sets up the touch and click listeners for the card and its action buttons.
         * Implements custom swipe-to-action physics.
         */
        @SuppressLint("ClickableViewAccessibility")
        private void setupListeners() {
            // Implement smooth horizontal swipe physics for revealable actions
            binding.projectCardView.setOnTouchListener(new View.OnTouchListener() {
                float startX = 0;
                float startY = 0; // Tracking vertical movement for scroll detection
                float startTranslateX = 0;
                boolean isSwiping = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            // Close any other open swipe menus before starting a new interaction
                            if (adapter.currentlySwipedView != null && adapter.currentlySwipedView != v) {
                                adapter.closeSwipedItem();
                            }
                            startX = event.getRawX();
                            startY = event.getRawY();
                            startTranslateX = v.getTranslationX();
                            isSwiping = false;
                            v.animate().cancel(); // Interrupt any ongoing snap animation
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dX = event.getRawX() - startX;
                            float dY = event.getRawY() - startY;

                            // Threshold check: ensure horizontal intent before blocking parent scroll
                            if (Math.abs(dX) > 15 && Math.abs(dX) > Math.abs(dY)) {
                                isSwiping = true;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            } else if (Math.abs(dY) > 15 && !isSwiping) {
                                // Vertical scrolling detected; allow RecyclerView to handle it
                                v.getParent().requestDisallowInterceptTouchEvent(false);
                            }

                            if (isSwiping) {
                                float newTranslateX = startTranslateX + dX;
                                float maxSwipe = binding.layoutActions.getWidth();

                                // Fallback measurement if the layout hasn't been drawn yet
                                if (maxSwipe == 0)
                                    maxSwipe = UiUtils.dpToPx(itemView.getContext(), 120);

                                // Clamp the card movement to only allow left-swiping up to the menu width
                                if (newTranslateX < -maxSwipe) newTranslateX = -maxSwipe;
                                if (newTranslateX > 0) newTranslateX = 0;

                                v.setTranslationX(newTranslateX);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!isSwiping) {
                                // To distinguish between a purposeful tap and an accidental drag, 
                                // we verify that the movement stayed within a small pixel threshold.
                                float finalDx = Math.abs(event.getRawX() - startX);
                                float finalDy = Math.abs(event.getRawY() - startY);

                                if (finalDx < 15 && finalDy < 15) {
                                    int pos = getBindingAdapterPosition();
                                    if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                                        adapter.listener.onProjectClick(adapter.projects.get(pos));
                                    }
                                }
                            } else {
                                // Interaction finished; snap the card to the nearest logic state (Open/Closed)
                                snapCardPosition(v);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            // If the RecyclerView intercepts the touch event (e.g., to perform a scroll),
                            // we must abort the click and smoothly snap the card back to its state.
                            if (isSwiping) {
                                snapCardPosition(v);
                            } else {
                                v.animate().translationX(0).setDuration(200).start();
                            }
                            return true;
                    }
                    return false;
                }

                /**
                 * Snaps the card to either the fully opened or fully closed state
                 * based on its current horizontal translation.
                 */
                private void snapCardPosition(View v) {
                    float finalTranslateX = v.getTranslationX();
                    float maxW = binding.layoutActions.getWidth();
                    if (maxW == 0) maxW = UiUtils.dpToPx(itemView.getContext(), 120);

                    if (finalTranslateX < -maxW / 2) {
                        // More than halfway open; snap to fully open
                        v.animate().translationX(-maxW).setDuration(200).start();
                        adapter.currentlySwipedView = v;
                    } else {
                        // Less than halfway open; snap back to closed
                        v.animate().translationX(0).setDuration(200).start();
                        if (adapter.currentlySwipedView == v) adapter.currentlySwipedView = null;
                    }
                }
            });

            binding.btnActionRename.setOnClickListener(v -> {
                adapter.closeSwipedItem();
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                    adapter.listener.onProjectRenameClick(adapter.projects.get(pos));
                }
            });

            binding.btnActionDelete.setOnClickListener(v -> {
                adapter.closeSwipedItem();
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                    adapter.listener.onProjectDeleteClick(adapter.projects.get(pos));
                }
            });
        }

        /**
         * Applies circular backgrounds with theme colors to the action buttons.
         */
        private void setupCircularActionButtons(Context context) {
            GradientDrawable renameBg = new GradientDrawable();
            renameBg.setShape(GradientDrawable.OVAL);
            renameBg.setColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));
            binding.btnActionRename.setBackground(renameBg);

            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(ContextCompat.getColor(context, R.color.vcode_accent_error));
            binding.btnActionDelete.setBackground(deleteBg);
        }

        /**
         * Applies specialized typefaces to the text components for better readability.
         */
        private void setupTypefaces(Context context) {
            binding.tvProjectName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvProjectInfo.setTypeface(FontManager.getInstance().getUiMedium(context));
        }

        /**
         * Binds a project model to the view holder, updating the UI with project details.
         *
         * @param project The project data to display.
         */
        public void bind(Project project) {
            currentBoundProjectId = project.getId();
            binding.tvProjectName.setText(project.getName());

            // Reset translation to prevent "ghost" open states on recycled views
            binding.projectCardView.setTranslationX(0);

            String timeAgo = DateUtils.getRelativeTime(new Date(project.getLastModifiedAt()));
            String info = project.getFileCount() + " files · " + timeAgo;
            binding.tvProjectInfo.setText(info);

            binding.badgesContainer.removeAllViews();
            File projectDir = new File(FileUtils.getProjectsDir(itemView.getContext()), project.getId());

            // Scan file system asynchronously to identify unique file types for badge display
            ExecutorProvider.getInstance().runOnIo(() -> {
                Set<String> extensions = new HashSet<>();
                scanUniqueExtensions(projectDir, extensions, 0);
                List<String> extList = new ArrayList<>(extensions);

                ExecutorProvider.getInstance().runOnMain(() -> {
                    // Safety check: ensure the ViewHolder hasn't been recycled for another project
                    if (!currentBoundProjectId.equals(project.getId())) return;
                    populateBadgesDynamically(extList);
                });
            });
        }

        /**
         * Generates and displays visual badges representing the file types in the project.
         * Automatically handles overflow with a "+N" badge if space is limited.
         */
        private void populateBadgesDynamically(List<String> extensions) {
            if (extensions.isEmpty()) return;
            Context ctx = itemView.getContext();
            int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
            int maxAllowedWidth = screenWidth - UiUtils.dpToPx(ctx, 150);

            int currentWidth = 0;
            int addedCount = 0;

            for (int i = 0; i < extensions.size(); i++) {
                String ext = extensions.get(i);
                TextView badge = createBadgeTextView(ctx, ext);

                badge.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int badgeWidth = badge.getMeasuredWidth() + UiUtils.dpToPx(ctx, 6);

                if (currentWidth + badgeWidth > maxAllowedWidth) {
                    int remaining = extensions.size() - addedCount;
                    TextView overflowBadge = createBadgeTextView(ctx, "+" + remaining);
                    binding.badgesContainer.addView(overflowBadge);
                    break;
                }

                binding.badgesContainer.addView(badge);
                currentWidth += badgeWidth;
                addedCount++;
            }
        }

        /**
         * Creates a styled TextView representing a file type badge.
         */
        private TextView createBadgeTextView(Context ctx, String text) {
            TextView tv = new TextView(ctx);
            tv.setText(text.toUpperCase());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tv.setTypeface(FontManager.getInstance().getUiSemiBold(ctx));

            int padX = UiUtils.dpToPx(ctx, 8);
            int padY = UiUtils.dpToPx(ctx, 4);
            tv.setPadding(padX, padY, padX, padY);

            // Specialized color contrast for JavaScript-family files
            boolean isJsLike = text.equalsIgnoreCase("js") || text.equalsIgnoreCase("mjs") || text.equalsIgnoreCase("cjs");
            int textColorRes = isJsLike ? R.color.vcode_lang_on_js : R.color.vcode_bg_card;
            tv.setTextColor(ContextCompat.getColor(ctx, textColorRes));

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(UiUtils.dpToPx(ctx, 6));

            int bgColorRes;
            FileType fileType = FileType.fromExtension(text);
            bgColorRes = fileType.getColorResId();

            bg.setColor(ContextCompat.getColor(ctx, bgColorRes));
            tv.setBackground(bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(UiUtils.dpToPx(ctx, 6));
            tv.setLayoutParams(params);

            return tv;
        }

        /**
         * Recursively scans the project directory to find unique file extensions.
         * Limited to a depth of 5 to avoid performance hits on large directories (e.g., node_modules).
         */
        private void scanUniqueExtensions(File dir, Set<String> exts, int depth) {
            if (dir == null || !dir.exists() || !dir.isDirectory() || depth > 5) return;

            File[] files = dir.listFiles();
            if (files == null) return;

            for (File f : files) {
                if (f.isDirectory()) {
                    String name = f.getName();
                    // Skip hidden or massive system folders to preserve performance
                    if (name.equals(".git") || name.equals("node_modules") || name.equals(".idea"))
                        continue;
                    scanUniqueExtensions(f, exts, depth + 1);
                } else {
                    String name = f.getName();
                    // Skip internal metadata files
                    if (name.equals(ProjectRepository.META_FILE) || name.equals(ProjectRepository.SESSION_FILE)) continue;

                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && dot < name.length() - 1) {
                        exts.add(name.substring(dot + 1).toLowerCase());
                    }
                }
            }
        }
    }
}