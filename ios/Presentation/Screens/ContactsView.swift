import SwiftUI

public struct ContactsView: View {
    @EnvironmentObject var env: AppEnvironment
    @State private var searchText: String = ""
    @State private var showImportSheet: Bool = false
    @State private var showNewContactSheet: Bool = false

    var filteredContacts: [ContactItem] {
        if searchText.isEmpty {
            return env.contacts
        } else {
            return env.contacts.filter {
                $0.fullName.localizedCaseInsensitiveContains(searchText) ||
                $0.phoneNumber.contains(searchText) ||
                ($0.company?.localizedCaseInsensitiveContains(searchText) ?? false)
            }
        }
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Search bar
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.gray)
                    TextField("Search contacts, phone, company...", text: $searchText)
                        .font(.subheadline)
                }
                .padding(10)
                .background(Color.waCardDark)
                .cornerRadius(10)
                .padding(.horizontal)
                .padding(.vertical, 8)

                List {
                    ForEach(filteredContacts) { contact in
                        HStack(spacing: 12) {
                            Circle()
                                .fill(Color.waGreen.opacity(0.2))
                                .frame(width: 44, height: 44)
                                .overlay {
                                    Text(String(contact.firstName.prefix(1)))
                                        .font(.headline.weight(.bold))
                                        .foregroundColor(.waGreen)
                                }

                            VStack(alignment: .leading, spacing: 3) {
                                Text(contact.fullName)
                                    .font(.subheadline.weight(.semibold))
                                Text(contact.phoneNumber)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                if let comp = contact.company {
                                    Text(comp)
                                        .font(.caption2)
                                        .foregroundColor(.waGreen)
                                }
                            }

                            Spacer()

                            Button {
                                env.sendMessage(contact: contact, text: "Hello \(contact.firstName), connecting with you via WA Marketing!")
                            } label: {
                                Image(systemName: "paperplane.fill")
                                    .font(.caption)
                                    .padding(8)
                                    .background(Color.waGreen.opacity(0.15))
                                    .foregroundColor(.waGreen)
                                    .clipShape(Circle())
                            }
                            .buttonStyle(.borderless)
                        }
                        .listRowBackground(Color.waBgDark)
                    }
                }
                .listStyle(.plain)
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Contacts & Leads")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showImportSheet = true
                    } label: {
                        Label("Import", systemImage: "arrow.down.doc")
                    }
                }
            }
            .sheet(isPresented: $showImportSheet) {
                iOSImportContactsSheet()
            }
        }
    }
}

struct iOSImportContactsSheet: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var env: AppEnvironment
    @State private var rawText: String = "Hamza Khan, +971501234567, Apex Dynamics, Dubai\nFatima Zahra, +971559876543, Emirates Trading, Abu Dhabi"
    @State private var isProcessing: Bool = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Paste or Upload Leads (CSV / Excel / PDF)")
                    .font(.headline)

                Text("Enter contact details in 'Name, Phone, Company, City' format. Binary Excel (.xlsx/.xls) and PDF (.pdf) documents are validated with the backend preview engine:")
                    .font(.caption)
                    .foregroundColor(.gray)

                TextEditor(text: $rawText)
                    .font(.caption)
                    .padding(8)
                    .background(Color.waCardDark)
                    .cornerRadius(8)
                    .frame(height: 180)

                Button {
                    isProcessing = true
                    let lines = rawText.components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
                    for (idx, line) in lines.enumerated() {
                        let parts = line.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                        if parts.count >= 2 {
                            let name = parts[0]
                            let phone = parts[1]
                            let company = parts.count > 2 ? parts[2] : "Imported"
                            let city = parts.count > 3 ? parts[3] : "Global"
                            let nameSplit = name.components(separatedBy: " ")
                            let first = nameSplit.first ?? "Lead"
                            let last = nameSplit.dropFirst().joined(separator: " ")
                            env.addContact(ContactItem(id: Int.random(in: 4000...9999) + idx, firstName: first, lastName: last, phoneNumber: phone, company: company, city: city))
                        }
                    }
                    isProcessing = false
                    dismiss()
                } label: {
                    HStack {
                        Spacer()
                        if isProcessing {
                            ProgressView()
                        } else {
                            Text("Confirm & Import Leads")
                                .fontWeight(.bold)
                        }
                        Spacer()
                    }
                    .padding()
                    .background(Color.waGreen)
                    .foregroundColor(.black)
                    .cornerRadius(10)
                }

                Spacer()
            }
            .padding()
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Import Leads")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
