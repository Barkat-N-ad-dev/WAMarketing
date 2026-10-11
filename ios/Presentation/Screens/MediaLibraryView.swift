import SwiftUI

public struct MediaLibraryView: View {
    @EnvironmentObject var env: AppEnvironment
    @State private var showUploadSheet: Bool = false

    public var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                    ForEach(env.mediaFiles) { file in
                        VStack(alignment: .leading, spacing: 8) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color.waBgDark)
                                    .frame(height: 100)

                                Image(systemName: iconForType(file.fileType))
                                    .font(.system(size: 32))
                                    .foregroundColor(file.fileType == "package" ? .waGreen : .blue)
                            }

                            Text(file.filename)
                                .font(.caption.weight(.bold))
                                .lineLimit(1)

                            HStack {
                                Text(file.fileType.uppercased())
                                    .font(.system(size: 8, weight: .bold))
                                    .padding(.horizontal, 4)
                                    .padding(.vertical, 2)
                                    .background(file.fileType == "package" ? Color.waGreen.opacity(0.2) : Color.blue.opacity(0.2))
                                    .foregroundColor(file.fileType == "package" ? .waGreen : .blue)
                                    .cornerRadius(4)

                                Spacer()

                                Text(formatBytes(file.sizeBytes))
                                    .font(.system(size: 9))
                                    .foregroundColor(.gray)
                            }

                            if let note = file.distributionNote {
                                Text(note)
                                    .font(.system(size: 8))
                                    .foregroundColor(.waGreen)
                                    .lineLimit(2)
                            }
                        }
                        .padding(10)
                        .background(Color.waCardDark)
                        .cornerRadius(12)
                    }
                }
                .padding()
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Media Library")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showUploadSheet = true
                    } label: {
                        Label("Upload", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $showUploadSheet) {
                iOSMediaUploadSheet()
            }
        }
    }

    private func iconForType(_ type: String) -> String {
        switch type {
        case "image": return "photo.fill"
        case "video": return "video.fill"
        case "package": return "app.badge.fill"
        default: return "doc.fill"
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let mb = Double(bytes) / (1024 * 1024)
        return String(format: "%.1f MB", mb)
    }
}

struct iOSMediaUploadSheet: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var env: AppEnvironment
    @State private var filename: String = "Brochure_\(Int.random(in: 100...999)).pdf"
    @State private var selectedType: String = "document"

    var body: some View {
        NavigationStack {
            Form {
                Section("Media Details") {
                    TextField("Filename", text: $filename)
                    Picker("File Type", selection: $selectedType) {
                        Text("Document (PDF)").tag("document")
                        Text("Image (JPG/PNG)").tag("image")
                        Text("Video (MP4)").tag("video")
                        Text("Android Package (APK)").tag("package")
                    }
                }

                Section("Distribution Mode") {
                    if selectedType == "package" {
                        Text("APK files are distributed via high-speed 1-click cloud download link embedded into recipient messages.")
                            .font(.caption)
                            .foregroundColor(.waGreen)
                    } else {
                        Text("Native WhatsApp Cloud API document/media messaging format.")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }

                Button("Save to Library") {
                    let newFile = MediaFileItem(
                        id: Int.random(in: 100...999),
                        filename: filename,
                        fileType: selectedType,
                        mimeType: selectedType == "package" ? "application/vnd.android.package-archive" : "application/pdf",
                        sizeBytes: 4500000,
                        url: "https://api.wamarketing.app/uploads/\(filename)",
                        isWhatsAppSupported: selectedType != "package",
                        distributionNote: selectedType == "package" ? "1-Click cloud link" : nil
                    )
                    env.addMediaFile(newFile)
                    dismiss()
                }
                .tint(.waGreen)
            }
            .navigationTitle("Upload Media")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
