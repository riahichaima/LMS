package com.course.lms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false) // required=false car on gère l'option A où JavaMailSender n'est pas dispo
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String token) {
        String verificationUrl = "http://localhost:8089/auth/verify?token=" + token;
        String subject = "Veuillez vérifier votre inscription";
        String content = "Bonjour !\n\nMerci de vous être inscrit. "
                + "Veuillez cliquer sur le lien ci-dessous pour vérifier votre adresse email :\n"
                + verificationUrl
                + "\n\nCe lien expirera dans 24 heures.";

        sendEmail(to, subject, content);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = "http://localhost:8089/auth/reset-password?token=" + token;
        String subject = "Réinitialisation de votre mot de passe";
        String content = "Bonjour !\n\nVous avez demandé à réinitialiser votre mot de passe. "
                + "Veuillez cliquer sur le lien ci-dessous pour créer un nouveau mot de passe (valable 15 minutes) :\n"
                + resetUrl + "\n\nSi vous n'avez pas fait cette demande, veuillez ignorer cet e-mail.";

        sendEmail(to, subject, content);
    }

    private void sendEmail(String to, String subject, String content) {
        if (mailSender != null) {
            // Option B ou C (Mailtrap ou Gmail) est configurée et active
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            message.setFrom("noreply@lms.com");
            try {
                mailSender.send(message);
                System.out.println("Email de vérification envoyé avec succès à " + to);
            } catch (Exception e) {
                System.err.println("Erreur lors de l'envoi de l'email à " + to + " : " + e.getMessage());
            }
        } else {
            // Option A : Affichage dans la console
            System.out.println("=========================================================");
            System.out.println("Email NON ENVoyé car configuration SMTP absente (Option A)");
            System.out.println("Destinataire : " + to);
            System.out.println("Sujet : " + subject);
            System.out.println("Contenu :\n" + content);
            System.out.println("=========================================================");
        }
    }
}
